package dev.limucc.histb.client.scan;

import net.minecraft.core.BlockPos;

/**
 * A single scan result. `min`/`max` are the world-space bounding box of the matched
 * structure (inclusive block coords) so the renderer can draw a highlight box.
 * For a single block, min == max == origin.
 */
public record Match(BlockPos origin, BlockPos min, BlockPos max, String patternName, String orientation) {
    public double distanceTo(BlockPos p) {
        return Math.sqrt(origin.distSqr(p));
    }
}
