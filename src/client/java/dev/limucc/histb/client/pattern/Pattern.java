package dev.limucc.histb.client.pattern;

import net.minecraft.world.level.block.state.BlockState;

/**
 * A runtime pattern: a box of BlockStates (null = air/wildcard cell).
 * Cells indexed [x][y][z] flattened as x*(sy*sz) + y*sz + z.
 */
public class Pattern {

    public final String name;
    public final int sx, sy, sz;
    public final BlockState[] cells;

    public Pattern(String name, int sx, int sy, int sz, BlockState[] cells) {
        this.name = name;
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.cells = cells;
    }

    public BlockState at(int x, int y, int z) {
        return cells[x * (sy * sz) + y * sz + z];
    }

    public int volume() { return sx * sy * sz; }

    public int solidCount() {
        int n = 0;
        for (BlockState s : cells) if (s != null) n++;
        return n;
    }
}
