package dev.limucc.histb.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.histb.client.config.ConfigManager;
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
 * Milestone 1 — single-block finder:
 *   - Key T: set the target = block you're looking at
 *   - Key G: scan loaded chunks within radius, print matches to chat
 * Structures, rotations, rendering and the GUI build on this.
 */
public class HistbClient implements ClientModInitializer {

    public static final String MOD_ID = "histb";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyMapping KEY_TARGET;
    public static KeyMapping KEY_SCAN;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        KEY_TARGET = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.histb.target", InputConstants.KEY_T, KeyMapping.Category.GAMEPLAY));
        KEY_SCAN = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.histb.scan", InputConstants.KEY_G, KeyMapping.Category.GAMEPLAY));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        LOGGER.info("HISTB loaded. Target block: T, Scan: G");
    }

    private void onTick(Minecraft mc) {
        while (KEY_TARGET.consumeClick()) setTargetFromCrosshair(mc);
        while (KEY_SCAN.consumeClick()) runScan(mc);
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

    private void runScan(Minecraft mc) {
        if (mc.player == null) return;
        if (Scanner.targetBlock == null) { overlay(mc, "§eNo target — look at a block and press T"); return; }
        if (Scanner.isRunning()) { overlay(mc, "§7Scan already running…"); return; }

        overlay(mc, "§7Scanning radius " + ConfigManager.get().scanRadius + "…");
        Scanner.scanAsync((matches, truncated) -> report(mc, matches, truncated));
    }

    private void report(Minecraft mc, List<Match> matches, boolean truncated) {
        if (mc.player == null) return;
        var cfg = ConfigManager.get();
        if (matches.isEmpty()) { overlay(mc, "§cNope — never seen that one"); return; }

        BlockPos p = mc.player.blockPosition();
        matches.sort((a, b) -> Double.compare(a.distanceTo(p), b.distanceTo(p)));

        overlay(mc, "§aSeen it! §a" + matches.size() + (truncated ? "+" : "") + " time(s)");
        if (cfg.chatCoords) {
            int shown = Math.min(matches.size(), cfg.maxMatches);
            for (int i = 0; i < shown; i++) {
                Match m = matches.get(i);
                BlockPos o = m.origin();
                int dist = (int) m.distanceTo(p);
                mc.player.sendSystemMessage(Component.literal(
                        "§e[HISTB] §f" + m.patternName()
                        + " §7@ §b" + o.getX() + " " + o.getY() + " " + o.getZ()
                        + " §7(" + dist + "m)"));
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
