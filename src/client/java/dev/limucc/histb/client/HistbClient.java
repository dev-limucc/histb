package dev.limucc.histb.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.histb.client.capture.RegionSelector;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.gui.PatternsScreen;
import dev.limucc.histb.client.render.HighlightRenderer;
import dev.limucc.histb.client.render.HighlightStore;
import dev.limucc.histb.client.scan.Match;
import dev.limucc.histb.client.scan.Scanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HISTB ("Haven't I Seen This Before?") client entrypoint.
 *
 * Keys:
 *   T  — set single-block target (look at a block)
 *   [  — set region corner 1 (look at a block)
 *   ]  — set region corner 2 (look at a block)
 *   K  — capture the selected region as a saved pattern
 *   G  — scan now (matches active patterns, else the single-block target)
 *   O  — open the Patterns manager (toggle active / delete / match settings)
 */
public class HistbClient implements ClientModInitializer {

    public static final String MOD_ID = "histb";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyMapping KEY_TARGET, KEY_SCAN, KEY_POS1, KEY_POS2, KEY_CAPTURE, KEY_OPEN;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        KEY_TARGET  = reg("key.histb.target",  InputConstants.KEY_T);
        KEY_SCAN    = reg("key.histb.scan",    InputConstants.KEY_G);
        KEY_POS1    = reg("key.histb.pos1",    InputConstants.KEY_LBRACKET);
        KEY_POS2    = reg("key.histb.pos2",    InputConstants.KEY_RBRACKET);
        KEY_CAPTURE = reg("key.histb.capture", InputConstants.KEY_K);
        KEY_OPEN    = reg("key.histb.open",    InputConstants.KEY_O);

        HighlightRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        LOGGER.info("HISTB loaded. T target, [ ] corners, K capture, G scan, O patterns.");
    }

    private static KeyMapping reg(String id, int key) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(id, key, KeyMapping.Category.GAMEPLAY));
    }

    private void onTick(Minecraft mc) {
        while (KEY_TARGET.consumeClick())  setTargetFromCrosshair(mc);
        while (KEY_POS1.consumeClick())    setCorner(mc, true);
        while (KEY_POS2.consumeClick())    setCorner(mc, false);
        while (KEY_CAPTURE.consumeClick()) overlay(mc, RegionSelector.capture(null));
        while (KEY_SCAN.consumeClick())    runScan(mc);
        while (KEY_OPEN.consumeClick())    { if (mc.screen == null) mc.setScreen(new PatternsScreen(null)); }
    }

    private void setTargetFromCrosshair(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            overlay(mc, "§eLook at a block, then press T");
            return;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        Scanner.targetBlock = block;
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        overlay(mc, "§aTarget: §f" + (id == null ? "?" : id.getPath()) + " §7(press G to scan)");
    }

    private void setCorner(Minecraft mc, boolean first) {
        if (mc.player == null || mc.level == null) return;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            overlay(mc, "§eLook at a block to set the corner");
            return;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        if (first) { RegionSelector.setPos1(pos); overlay(mc, "§aCorner 1: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()); }
        else       { RegionSelector.setPos2(pos); overlay(mc, "§aCorner 2: §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()); }
        if (RegionSelector.hasBothCorners()) overlay(mc, "§7Both corners set — press K to capture");
    }

    private void runScan(Minecraft mc) {
        if (mc.player == null) return;
        boolean hasActive = ConfigManager.get().patterns.stream().anyMatch(p -> p.active);
        if (!hasActive && Scanner.targetBlock == null) {
            overlay(mc, "§eNothing to find — capture a pattern (K) or set a target block (T)");
            return;
        }
        if (Scanner.isRunning()) { overlay(mc, "§7Scan already running…"); return; }
        overlay(mc, "§7Scanning radius " + ConfigManager.get().scanRadius + "…");
        Scanner.scanAsync((matches, truncated) -> report(mc, matches, truncated));
    }

    private void report(Minecraft mc, List<Match> matches, boolean truncated) {
        if (mc.player == null) return;
        var cfg = ConfigManager.get();
        if (matches.isEmpty()) { HighlightStore.clear(); overlay(mc, "§cNope — never seen that one"); return; }

        BlockPos p = mc.player.blockPosition();
        matches.sort((a, b) -> Double.compare(a.distanceTo(p), b.distanceTo(p)));

        // Feed the highlight renderer
        HighlightStore.set(matches);

        overlay(mc, "§aSeen it! §a" + matches.size() + (truncated ? "+" : "") + " time(s)");
        if (cfg.chatCoords) {
            int shown = Math.min(matches.size(), cfg.maxMatches);
            for (int i = 0; i < shown; i++) {
                Match m = matches.get(i);
                BlockPos o = m.origin();
                int dist = (int) m.distanceTo(p);
                String orient = "—".equals(m.orientation()) ? "" : " §8[" + m.orientation() + "]";
                mc.player.sendSystemMessage(Component.literal(
                        "§e[HISTB] §f" + m.patternName()
                        + " §7@ §b" + o.getX() + " " + o.getY() + " " + o.getZ()
                        + " §7(" + dist + "m)" + orient));
            }
            if (truncated) {
                mc.player.sendSystemMessage(
                        Component.literal("§7… more hidden (raise Max Matches in config)"));
            }
        }
    }

    private static void overlay(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal(msg));
    }
}
