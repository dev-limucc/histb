package dev.limucc.histb.client.capture;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-corner region selection + capture (WorldEdit/Litematica style).
 *
 *   - setPos1 / setPos2: store the block you're looking at as a corner.
 *   - capture(name): read every block in the box between the two corners,
 *     serialize each BlockState to a string, and save it as a named SavedPattern
 *     in the config (marked active). Air cells are stored as "" (wildcard-friendly).
 *     Cells flatten as x*(sy*sz) + y*sz + z to match Pattern.at(x,y,z).
 */
public final class RegionSelector {

    private RegionSelector() {}

    public static BlockPos pos1 = null;
    public static BlockPos pos2 = null;

    public static void setPos1(BlockPos p) { pos1 = p; }
    public static void setPos2(BlockPos p) { pos2 = p; }

    public static boolean hasBothCorners() { return pos1 != null && pos2 != null; }

    public static String capture(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "§cNo world";
        if (!hasBothCorners()) return "§eSet both corners first ([ and ], looking at blocks)";

        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        long volume = (long) sx * sy * sz;
        if (volume > 32768) return "§cToo big (" + volume + " blocks, max 32768)";

        List<String> cells = new ArrayList<>((int) volume);
        int solid = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    m.set(minX + x, minY + y, minZ + z);
                    BlockState st = mc.level.getBlockState(m);
                    if (st.isAir()) {
                        cells.add("");
                    } else {
                        cells.add(BlockStateParser.serialize(st));
                        solid++;
                    }
                }
            }
        }

        ModConfig.SavedPattern sp = new ModConfig.SavedPattern();
        sp.name = (name == null || name.isBlank())
                ? ("pattern" + (ConfigManager.get().patterns.size() + 1)) : name.trim();
        sp.active = true;
        sp.sizeX = sx; sp.sizeY = sy; sp.sizeZ = sz;
        sp.cells = cells;
        ConfigManager.get().patterns.add(sp);
        ConfigManager.save();

        HistbClient.LOGGER.info("Captured pattern '{}' {}x{}x{} ({} solid)", sp.name, sx, sy, sz, solid);
        return "§aCaptured §f" + sp.name + " §7(" + sx + "×" + sy + "×" + sz + ", " + solid + " blocks)";
    }

    public static void clear() { pos1 = null; pos2 = null; }
}
