package dev.limucc.histb.client.scan;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.pattern.Orientation;
import dev.limucc.histb.client.pattern.Pattern;
import dev.limucc.histb.client.pattern.PatternStore;
import dev.limucc.histb.client.render.HighlightStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Continuous, X-ray-style scanner.
 *
 * When the mod is enabled, a throttled background loop re-scans the player's
 * loaded area (render distance) and publishes matches to the HighlightStore, which
 * the renderer draws every frame. No manual trigger, no radius picker, no cap.
 *
 * Performance: anchor-based matching with section-palette culling, run off the
 * main thread; one scan in flight at a time; re-scan at most every RESCAN_MS.
 */
public class Scanner {

    private static final long RESCAN_MS = 1500;

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static volatile long lastScanEnd = 0L;

    /** Called every client tick. Kicks off a background scan when due. */
    public static void tick() {
        ModConfig cfg = ConfigManager.get();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.enabled || mc.level == null || mc.player == null) {
            HighlightStore.clear();
            return;
        }
        if (running.get()) return;
        if (System.currentTimeMillis() - lastScanEnd < RESCAN_MS) return;
        if (ConfigManager.get().patterns.stream().noneMatch(p -> p.active)) {
            HighlightStore.clear();
            return;
        }
        kickScan(mc);
    }

    private static void kickScan(Minecraft mc) {
        if (!running.compareAndSet(false, true)) return;
        ClientLevel level = mc.level;
        BlockPos center = mc.player.blockPosition();
        // "loaded area" = render distance in blocks (X-ray style), capped for safety.
        int rd = mc.options.getEffectiveRenderDistance();
        int radius = Math.max(32, Math.min(160, rd * 16));

        List<Pattern> patterns = PatternStore.activePatterns();
        List<Orientation> orients = Orientation.enabled(ConfigManager.get());
        ModConfig.MatchMode strict = ConfigManager.get().matchMode;

        Thread worker = new Thread(() -> {
            List<Match> out = new ArrayList<>();
            try {
                if (!patterns.isEmpty()) scanPatterns(level, center, radius, patterns, orients, strict, out);
            } catch (Throwable t) {
                HistbClient.LOGGER.error("Scan failed", t);
            } finally {
                lastScanEnd = System.currentTimeMillis();
                running.set(false);
            }
            mc.execute(() -> HighlightStore.set(out));
        }, "histb-scan");
        worker.setDaemon(true);
        worker.start();
    }

    // ── Multi-block pattern matching (anchor-based, section-palette culled) ───
    //
    // Instead of testing every block in render distance, we walk chunk SECTIONS and
    // skip any 16³ section whose palette can't contain a pattern's anchor block
    // (LevelChunkSection.maybeHas) and any all-air section. This is Litematica's trick
    // and turns "scan every block" into "scan only sections that actually contain the
    // anchor" — typically a 10–100× cut, which is the main FPS win and lets big
    // patterns scan cheaply.
    private static void scanPatterns(ClientLevel level, BlockPos center, int radius,
                                     List<Pattern> patterns, List<Orientation> orients,
                                     ModConfig.MatchMode strict, List<Match> out) {
        long r2 = (long) radius * radius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        // Precompute each pattern's anchor block + a per-section predicate.
        int n = patterns.size();
        var anchors = new int[n][];
        var anchorBlocks = new net.minecraft.world.level.block.Block[n];
        for (int i = 0; i < n; i++) {
            anchors[i] = firstSolid(patterns.get(i));
            if (anchors[i] != null) anchorBlocks[i] = patterns.get(i).at(anchors[i][0], anchors[i][1], anchors[i][2]).getBlock();
        }

        int minCX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxCX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minCZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxCZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        int minSecY = SectionPos.blockToSectionCoord(Math.max(level.getMinY(), center.getY() - radius));
        int maxSecY = SectionPos.blockToSectionCoord(Math.min(level.getMaxY(), center.getY() + radius));

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                var chunk = level.getChunk(cx, cz);

                for (int secY = minSecY; secY <= maxSecY; secY++) {
                    int secIdx = level.getSectionIndex(SectionPos.sectionToBlockCoord(secY));
                    var sections = chunk.getSections();
                    if (secIdx < 0 || secIdx >= sections.length) continue;
                    var section = sections[secIdx];
                    if (section == null || section.hasOnlyAir()) continue;

                    // Which patterns' anchors might be in this section?
                    for (int i = 0; i < n; i++) {
                        if (anchors[i] == null) continue;
                        final var ab = anchorBlocks[i];
                        if (!section.maybeHas(s -> s.is(ab))) continue; // PALETTE CULL — the big win

                        Pattern p = patterns.get(i);
                        int[] anchor = anchors[i];
                        int baseX = cx << 4, baseY = SectionPos.sectionToBlockCoord(secY), baseZ = cz << 4;

                        for (int lx = 0; lx < 16; lx++) {
                            int wx = baseX + lx;
                            for (int lz = 0; lz < 16; lz++) {
                                int wz = baseZ + lz;
                                for (int ly = 0; ly < 16; ly++) {
                                    int wy = baseY + ly;
                                    cursor.set(wx, wy, wz);
                                    if (center.distSqr(cursor) > r2) continue;
                                    if (!section.getBlockState(lx, ly, lz).is(ab)) continue;

                                    for (Orientation o : orients) {
                                        int ox = wx - o.tx(anchor[0], anchor[1], anchor[2]);
                                        int oy = wy - o.ty(anchor[0], anchor[1], anchor[2]);
                                        int oz = wz - o.tz(anchor[0], anchor[1], anchor[2]);
                                        if (matchesAt(level, p, o, ox, oy, oz, strict, probe)) {
                                            int[] bb = worldBounds(p, o, ox, oy, oz);
                                            out.add(new Match(new BlockPos(ox, oy, oz),
                                                    new BlockPos(bb[0], bb[1], bb[2]),
                                                    new BlockPos(bb[3], bb[4], bb[5]),
                                                    p.name, o.label));
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean matchesAt(ClientLevel level, Pattern p, Orientation o,
                                     int ox, int oy, int oz, ModConfig.MatchMode strict,
                                     BlockPos.MutableBlockPos probe) {
        for (int x = 0; x < p.sx; x++) {
            for (int y = 0; y < p.sy; y++) {
                for (int z = 0; z < p.sz; z++) {
                    BlockState want = p.at(x, y, z);
                    boolean wantAir = (want == null);
                    if (wantAir && strict == ModConfig.MatchMode.IGNORE_AIR) continue;
                    int wx = ox + o.tx(x, y, z);
                    int wy = oy + o.ty(x, y, z);
                    int wz = oz + o.tz(x, y, z);
                    probe.set(wx, wy, wz);
                    if (!level.hasChunk(SectionPos.blockToSectionCoord(wx), SectionPos.blockToSectionCoord(wz))) return false;
                    BlockState got = level.getBlockState(probe);
                    if (wantAir) { if (!got.isAir()) return false; continue; }
                    switch (strict) {
                        case EXACT      -> { if (got != want) return false; }
                        case BLOCK_ONLY, IGNORE_AIR -> { if (!got.is(want.getBlock())) return false; }
                    }
                }
            }
        }
        return true;
    }

    private static int[] worldBounds(Pattern p, Orientation o, int ox, int oy, int oz) {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE;
        int maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        int[][] corners = {
            {0,0,0},{p.sx-1,0,0},{0,p.sy-1,0},{0,0,p.sz-1},
            {p.sx-1,p.sy-1,0},{p.sx-1,0,p.sz-1},{0,p.sy-1,p.sz-1},{p.sx-1,p.sy-1,p.sz-1}
        };
        for (int[] c : corners) {
            int wx=ox+o.tx(c[0],c[1],c[2]), wy=oy+o.ty(c[0],c[1],c[2]), wz=oz+o.tz(c[0],c[1],c[2]);
            minX=Math.min(minX,wx); minY=Math.min(minY,wy); minZ=Math.min(minZ,wz);
            maxX=Math.max(maxX,wx); maxY=Math.max(maxY,wy); maxZ=Math.max(maxZ,wz);
        }
        return new int[]{minX,minY,minZ,maxX,maxY,maxZ};
    }

    private static int[] firstSolid(Pattern p) {
        for (int x = 0; x < p.sx; x++)
            for (int y = 0; y < p.sy; y++)
                for (int z = 0; z < p.sz; z++)
                    if (p.at(x, y, z) != null) return new int[]{x, y, z};
        return null;
    }

    private record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {}
    private static Bounds bounds(ClientLevel level, BlockPos c, int r) {
        return new Bounds(
            c.getX()-r, Math.max(level.getMinY(), c.getY()-r), c.getZ()-r,
            c.getX()+r, Math.min(level.getMaxY(), c.getY()+r), c.getZ()+r);
    }
}
