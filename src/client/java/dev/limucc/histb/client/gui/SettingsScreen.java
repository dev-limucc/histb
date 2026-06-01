package dev.limucc.histb.client.gui;

import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Match + display settings. No radius/scan-mode/max-matches — scanning is automatic. */
public class SettingsScreen extends Screen {

    private final Screen parent;

    public SettingsScreen(Screen parent) {
        super(Component.literal("HISTB? — Match & Display"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 44, w = 280, h = 20, gap = 24;

        addRenderableWidget(Button.builder(strictLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.matchMode = switch (c.matchMode) {
                case EXACT -> ModConfig.MatchMode.BLOCK_ONLY;
                case BLOCK_ONLY -> ModConfig.MatchMode.IGNORE_AIR;
                case IGNORE_AIR -> ModConfig.MatchMode.EXACT;
            };
            ConfigManager.save(); b.setMessage(strictLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(rotLabel(), b -> {
            ModConfig c = ConfigManager.get();
            if (c.rotateY && !c.mirror && !c.rotateX) { c.mirror = true; }
            else if (c.rotateY && c.mirror && !c.rotateX) { c.rotateX = true; c.rotateZ = true; }
            else if (c.rotateX) { c.rotateY = false; c.rotateX = false; c.rotateZ = false; c.mirror = false; }
            else { c.rotateY = true; c.mirror = false; c.rotateX = false; c.rotateZ = false; }
            ConfigManager.save(); b.setMessage(rotLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(styleLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.highlightStyle = switch (c.highlightStyle) {
                case LINES -> ModConfig.HighlightStyle.FILLED;
                case FILLED -> ModConfig.HighlightStyle.BOTH;
                case BOTH -> ModConfig.HighlightStyle.LINES;
            };
            ConfigManager.save(); b.setMessage(styleLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(colorLabel(), b -> {
            ModConfig c = ConfigManager.get();
            int[] pal = {0x00E0FF, 0xFF3030, 0x30FF30, 0xFFE030, 0xFF30FF, 0xFFFFFF, 0xFF8000};
            int i = 0; for (; i < pal.length; i++) if (pal[i] == c.boxColor) break;
            c.boxColor = pal[(i + 1) % pal.length];
            ConfigManager.save(); b.setMessage(colorLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(wallsLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.throughWalls = !c.throughWalls;
            ConfigManager.save(); b.setMessage(wallsLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap;

        addRenderableWidget(Button.builder(chatLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.chatCoords = !c.chatCoords;
            ConfigManager.save(); b.setMessage(chatLabel());
        }).bounds(cx - w/2, y, w, h).build()); y += gap + 8;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 50, y, 100, h).build());
    }

    private Component strictLabel() {
        String s = switch (ConfigManager.get().matchMode) {
            case EXACT -> "EXACT (block+state)"; case BLOCK_ONLY -> "BLOCK TYPE ONLY"; case IGNORE_AIR -> "IGNORE AIR";
        };
        return Component.literal("Match: " + s);
    }
    private Component rotLabel() {
        ModConfig c = ConfigManager.get();
        String s;
        if (!c.rotateY && !c.rotateX && !c.rotateZ && !c.mirror) s = "none";
        else if (c.rotateX || c.rotateZ) s = "all + mirror";
        else if (c.mirror) s = "Y + mirror";
        else s = "Y rotations";
        return Component.literal("Rotations: " + s);
    }
    private Component styleLabel() {
        String s = switch (ConfigManager.get().highlightStyle) { case LINES -> "LINES"; case FILLED -> "FILLED"; case BOTH -> "BOTH"; };
        return Component.literal("Highlight style: " + s);
    }
    private Component colorLabel() { return Component.literal(String.format("Highlight color: §b#%06X", ConfigManager.get().boxColor)); }
    private Component wallsLabel() { return Component.literal("Through walls: " + (ConfigManager.get().throughWalls ? "§aON" : "§cOFF")); }
    private Component chatLabel()  { return Component.literal("Chat coordinates: " + (ConfigManager.get().chatCoords ? "§aON" : "§cOFF")); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 18, 0xFFFFFFFF);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }
}
