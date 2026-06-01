package dev.limucc.histb.client.pattern;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the active SavedPatterns in config into runtime Patterns (BlockState[] cells).
 * Rebuilt whenever the config changes or before a scan. A null cell = air/wildcard.
 */
public final class PatternStore {

    private PatternStore() {}

    /** Build runtime Patterns for every ACTIVE saved pattern. Empty if none active. */
    public static List<Pattern> activePatterns() {
        List<Pattern> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return out;

        HolderLookup<net.minecraft.world.level.block.Block> lookup =
                mc.level.registryAccess().lookupOrThrow(Registries.BLOCK);

        for (ModConfig.SavedPattern sp : ConfigManager.get().patterns) {
            if (!sp.active) continue;
            int n = sp.sizeX * sp.sizeY * sp.sizeZ;
            if (sp.cells == null || sp.cells.size() != n) {
                HistbClient.LOGGER.warn("Skipping malformed pattern '{}'", sp.name);
                continue;
            }
            BlockState[] cells = new BlockState[n];
            for (int i = 0; i < n; i++) {
                String s = sp.cells.get(i);
                if (s == null || s.isEmpty()) { cells[i] = null; continue; } // air/wildcard
                try {
                    cells[i] = BlockStateParser.parseForBlock(lookup, s, false).blockState();
                } catch (Exception e) {
                    cells[i] = null; // unknown block → treat as wildcard rather than fail
                }
            }
            out.add(new Pattern(sp.name, sp.sizeX, sp.sizeY, sp.sizeZ, cells));
        }
        return out;
    }
}
