package dev.limucc.histb.client.scan;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.pattern.Orientation;
import dev.limucc.histb.client.pattern.Pattern;
import dev.limucc.histb.client.pattern.PatternStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scanner. Two modes:
 *   - If any saved pattern is ACTIVE → multi-block structure matching (anchor-based,
 *     with enabled rotations/mirrors and the configured strictness).
 *   - Else if a single-block target is set → fast single-block find (milestone 1).
 *
 * Runs off the main thread; results handed back on the client thread via mc.execute.
 */
public class Scanner {

    private static final AtomicBoolean running = new AtomicBoolean(false);

    /** Single-block fallback target (set via the T key). */
    public static volatile Block targetBlock = null;

    public interface ResultSink { void accept(List<Match> matches, boolean truncated); }

    public static boolean isRunning() { return running.get(); }

    public static void scanAsync(ResultSink onDone) {
        if (!running.compareAndSet(false, true)) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { running.set(false); return; }

        BlockPos center = mc.player.blockPosition();
        int radius = Math.max(16, Math.min(256, ConfigManager.get().scanRadius));
        int maxMatches = Math.max(1, ConfigManager.get().maxMatches);
        ModConfig cfg = ConfigManager.get();

        // Build runtime patterns on the client thread (registry access) before going async.
        List<Pattern> patterns = PatternStore.activePatterns();
        Block single = targetBlock;
        List<Orientation> orients = Orientation.enabled(cfg);
        ModConfig.MatchMode strict = cfg.matchMode;

        Thread worker = new Thread(() -> {
            List<Match> out = new ArrayList<>();
            boolean truncated = false;
            try {
                if (!patterns.isEmpty()) {
                    truncated = scanPatterns(level, center, radius, patterns, orients, strict, maxMatches, out);
                } else if (single != null) {
                    truncated = scanSingle(level, center, radius, single, maxMatches, out);
                }
            } catch (Throwable t) {
                HistbClient.LOGGER.error("Scan failed", t);
            } finally {
                running.set(false);
            }
            final boolean tr = truncated;
            mc.execute(() -> onDone.accept(out, tr));
        }, "histb-scan");
        worker.setDaemon(true);
        worker.start();
    }

    // ── Single-block (milestone 1) ────────────────────────────────────────────
    private static boolean scanSingle(ClientLevel level, BlockPos center, int radius,
                                      Block target, int maxMatches, List<Match> out) {
        long r2 = (long) radius * radius;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        Bounds b = bounds(level, center, radius);
        for (int wx = b.minX; wx <= b.maxX; wx++)
            for (int wy = b.minY; wy <= b.maxY; wy++)
                for (int wz = b.minZ; wz <= b.maxZ; wz++) {
                    m.set(wx, wy, wz);
                    if (center.distSqr(m) > r2) continue;
                    if (!level.hasChunk(SectionPos.blockToSectionCoord(wx), SectionPos.blockToSectionCoord(wz))) continue;
                    if (level.getBlockState(m).is(target)) {
                        out.add(new Match(m.immutable(), keyName(target), "—"));
                        if (out.size() >= maxMatches) return true;
                    }
                }
        return false;
    }

    // ── Multi-block pattern matching (anchor-based) ───────────────────────────
    private static boolean scanPatterns(ClientLevel level, BlockPos center, int radius,
                                        List<Pattern> patterns, List<Orientation> orients,
                                        ModConfig.MatchMode strict, int maxMatches, List<Match> out) {
        long r2 = (long) radius * radius;
        Bounds b = bounds(level, center, radius);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

        // Precompute each pattern's anchor: first non-air cell.
        for (Pattern p : patterns) {
            int[] anchor = firstSolid(p); // {x,y,z} local, or null
            if (anchor == null) continue;
            BlockState anchorState = p.at(anchor[0], anchor[1], anchor[2]);
            Block anchorBlock = anchorState.getBlock();

            for (int wx = b.minX; wx <= b.maxX; wx++) {
                for (int wy = b.minY; wy <= b.maxY; wy++) {
                    for (int wz = b.minZ; wz <= b.maxZ; wz++) {
                        if (!level.hasChunk(SectionPos.blockToSectionCoord(wx), SectionPos.blockToSectionCoord(wz))) continue;
                        cursor.set(wx, wy, wz);
                        if (center.distSqr(cursor) > r2) continue;
                        if (!level.getBlockState(cursor).is(anchorBlock)) continue;

                        // This world cell matches the anchor block. Try each orientation:
                        for (Orientation o : orients) {
                            // world origin so that anchor maps onto (wx,wy,wz)
                            int ax = o.tx(anchor[0], anchor[1], anchor[2]);
                            int ay = o.ty(anchor[0], anchor[1], anchor[2]);
                            int az = o.tz(anchor[0], anchor[1], anchor[2]);
                            int ox = wx - ax, oy = wy - ay, oz = wz - az;
                            if (matchesAt(level, p, o, ox, oy, oz, strict, probe)) {
                                out.add(new Match(new BlockPos(ox, oy, oz), p.name, o.label));
                                if (out.size() >= maxMatches) return true;
                                break; // don't double-count same origin via other orientations
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesAt(ClientLevel level, Pattern p, Orientation o,
                                     int ox, int oy, int oz, ModConfig.MatchMode strict,
                                     BlockPos.MutableBlockPos probe) {
        for (int x = 0; x < p.sx; x++) {
            for (int y = 0; y < p.sy; y++) {
                for (int z = 0; z < p.sz; z++) {
                    BlockState want = p.at(x, y, z);
                    boolean wantAir = (want == null);
                    if (wantAir && strict == ModConfig.MatchMode.IGNORE_AIR) continue; // wildcard
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

    private static String keyName(Block b) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        return id == null ? "block" : id.getPath();
    }
}
