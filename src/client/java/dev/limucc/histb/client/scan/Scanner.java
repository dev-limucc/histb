package dev.limucc.histb.client.scan;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.config.ConfigManager;
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
 * Milestone 1: single-block finder.
 * Scans loaded chunk sections within the configured radius for a target block.
 * Runs off the main thread; results handed back on the client thread.
 */
public class Scanner {

    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static volatile Block targetBlock = null;

    public interface ResultSink { void accept(List<Match> matches, boolean truncated); }

    public static boolean isRunning() { return running.get(); }

    public static void scanAsync(ResultSink onDone) {
        if (!running.compareAndSet(false, true)) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Block target = targetBlock;
        if (level == null || mc.player == null || target == null) {
            running.set(false);
            return;
        }

        BlockPos center = mc.player.blockPosition();
        int radius = Math.max(16, Math.min(256, ConfigManager.get().scanRadius));
        int maxMatches = Math.max(1, ConfigManager.get().maxMatches);

        Thread worker = new Thread(() -> {
            List<Match> out = new ArrayList<>();
            boolean truncated = false;
            try {
                truncated = scan(level, center, radius, target, maxMatches, out);
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

    private static boolean scan(ClientLevel level, BlockPos center, int radius,
                                Block target, int maxMatches, List<Match> out) {
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minY = Math.max(level.getMinY(), center.getY() - radius);
        int maxY = Math.min(level.getMaxY(), center.getY() + radius);
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;

        int secMinX = SectionPos.blockToSectionCoord(minX);
        int secMaxX = SectionPos.blockToSectionCoord(maxX);
        int secMinY = SectionPos.blockToSectionCoord(minY);
        int secMaxY = SectionPos.blockToSectionCoord(maxY);
        int secMinZ = SectionPos.blockToSectionCoord(minZ);
        int secMaxZ = SectionPos.blockToSectionCoord(maxZ);

        long r2 = (long) radius * radius;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int scx = secMinX; scx <= secMaxX; scx++) {
            for (int scz = secMinZ; scz <= secMaxZ; scz++) {
                if (!level.hasChunk(scx, scz)) continue;
                for (int scy = secMinY; scy <= secMaxY; scy++) {
                    int baseX = SectionPos.sectionToBlockCoord(scx);
                    int baseY = SectionPos.sectionToBlockCoord(scy);
                    int baseZ = SectionPos.sectionToBlockCoord(scz);
                    for (int dx = 0; dx < 16; dx++) {
                        int wx = baseX + dx;
                        if (wx < minX || wx > maxX) continue;
                        for (int dy = 0; dy < 16; dy++) {
                            int wy = baseY + dy;
                            if (wy < minY || wy > maxY) continue;
                            for (int dz = 0; dz < 16; dz++) {
                                int wz = baseZ + dz;
                                if (wz < minZ || wz > maxZ) continue;
                                m.set(wx, wy, wz);
                                if (center.distSqr(m) > r2) continue;
                                BlockState st = level.getBlockState(m);
                                if (st.is(target)) {
                                    out.add(new Match(m.immutable(), keyName(target), "—"));
                                    if (out.size() >= maxMatches) return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static String keyName(Block b) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
        return id == null ? "block" : id.getPath();
    }
}
