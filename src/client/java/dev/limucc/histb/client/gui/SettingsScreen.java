package dev.limucc.histb.client.gui;

import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Scan + output settings (no keybinds needed — all here). */
public class SettingsScreen extends Screen {

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.literal("HISTB? — Scan Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 44, w = 260, h = 20, gap = 24;

        addRenderableWidget(Button.builder(radiusLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.scanRadius = switch (c.scanRadius) {
                case 32 -> 64; case 64 -> 96; case 96 -> 128; case 128 -> 192; case 192 -> 256;
                default -> 32;
            };
            ConfigManager.save(); b.setMessage(radiusLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(scanModeLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.scanMode = (c.scanMode == ModConfig.ScanMode.MANUAL) ? ModConfig.ScanMode.AUTO : ModConfig.ScanMode.MANUAL;
            ConfigManager.save(); b.setMessage(scanModeLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(chatLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.chatCoords = !c.chatCoords;
            ConfigManager.save(); b.setMessage(chatLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(maxLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.maxMatches = switch (c.maxMatches) {
                case 10 -> 25; case 25 -> 50; case 50 -> 100; case 100 -> 250; default -> 10;
            };
            ConfigManager.save(); b.setMessage(maxLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(colorLabel(), b -> {
            ModConfig c = ConfigManager.get();
            int[] palette = {0x00E0FF, 0xFF3030, 0x30FF30, 0xFFE030, 0xFF30FF, 0xFFFFFF, 0xFF8000};
            int i = 0; for (; i < palette.length; i++) if (palette[i] == c.boxColor) break;
            c.boxColor = palette[(i + 1) % palette.length];
            ConfigManager.save(); b.setMessage(colorLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(lifeLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.highlightSeconds = switch (c.highlightSeconds) {
                case 0 -> 15; case 15 -> 30; case 30 -> 60; case 60 -> 120; default -> 0;
            };
            ConfigManager.save(); b.setMessage(lifeLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap + 8;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 50, y, 100, h).build());
    }

    private Component radiusLabel()   { return Component.literal("Scan Radius: " + ConfigManager.get().scanRadius + " blocks"); }
    private Component scanModeLabel() { return Component.literal("Scan Mode: " + ConfigManager.get().scanMode.name() + (ConfigManager.get().scanMode == ModConfig.ScanMode.AUTO ? " (auto re-scan)" : " (press key)")); }
    private Component chatLabel()     { return Component.literal("Chat coordinates: " + (ConfigManager.get().chatCoords ? "§aON" : "§cOFF")); }
    private Component maxLabel()      { return Component.literal("Max matches: " + ConfigManager.get().maxMatches); }
    private Component colorLabel()    { return Component.literal(String.format("Highlight color: §b#%06X", ConfigManager.get().boxColor)); }
    private Component lifeLabel()     { int s = ConfigManager.get().highlightSeconds; return Component.literal("Highlight lasts: " + (s == 0 ? "until next scan" : s + "s")); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 18, 0xFFFFFFFF);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }
}
