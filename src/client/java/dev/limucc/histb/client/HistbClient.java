package dev.limucc.histb.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.gui.HistbConfigScreen;
import dev.limucc.histb.client.render.HighlightRenderer;
import dev.limucc.histb.client.render.HighlightStore;
import dev.limucc.histb.client.scan.Scanner;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HISTB? ("Haven't I Seen This Before?") — X-ray-style structure finder.
 *
 * When enabled, it continuously scans the loaded area for your active patterns
 * (loaded from .litematic / .nbt) and highlights every match in real time.
 *
 * Keybinds (UNBOUND by default — bind in Options → Controls, or use ModMenu):
 *   - Toggle on/off
 *   - Open the GUI (hub)
 */
public class HistbClient implements ClientModInitializer {

    public static final String MOD_ID = "histb";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static KeyMapping KEY_TOGGLE, KEY_OPEN;

    @Override
    public void onInitializeClient() {
        ConfigManager.load();

        int none = InputConstants.UNKNOWN.getValue();
        KEY_TOGGLE = reg("key.histb.toggle", none);
        KEY_OPEN   = reg("key.histb.open",   none);

        HighlightRenderer.register();
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        // Stop scanning the moment we leave a world, so the background worker can never
        // touch chunks while the integrated server is saving/unloading them (which could
        // hang the "Saving world…" screen on quit).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Scanner.onWorldLeave());

        LOGGER.info("HISTB? loaded. Toggle + Open are unbound by default; open via ModMenu.");
    }

    private static KeyMapping reg(String id, int key) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping(id, key, KeyMapping.Category.GAMEPLAY));
    }

    private void onTick(Minecraft mc) {
        while (KEY_TOGGLE.consumeClick()) toggle(mc);
        while (KEY_OPEN.consumeClick()) { if (mc.screen == null) mc.setScreen(new HistbConfigScreen(null)); }

        // Continuous X-ray-style scan
        Scanner.tick();
    }

    public static void toggle(Minecraft mc) {
        ModConfig cfg = ConfigManager.get();
        cfg.enabled = !cfg.enabled;
        ConfigManager.save();
        if (!cfg.enabled) HighlightStore.clear();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal(
                    "§e[HISTB?] " + (cfg.enabled ? "§aON" : "§cOFF")));
        }
    }
}
