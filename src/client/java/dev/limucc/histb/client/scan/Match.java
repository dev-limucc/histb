package dev.limucc.histb.client.scan;

import net.minecraft.core.BlockPos;

/** A single scan result: where a pattern was found. */
public record Match(BlockPos origin, String patternName, String orientation) {
    public double distanceTo(BlockPos p) {
        return Math.sqrt(origin.distSqr(p));
    }
}
