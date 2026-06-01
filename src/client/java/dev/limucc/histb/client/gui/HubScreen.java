package dev.limucc.histb.client.gui;

import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.render.HighlightStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main hub — everything reachable here (no keybinds required). Opens from ModMenu.
 */
public class HubScreen extends Screen {

    private final Screen parent;

    public HubScreen(Screen parent) {
        super(Component.literal("Haven't I Seen This Before?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 64;
        int w = 240, h = 20, gap = 24;

        addRenderableWidget(Button.builder(enableLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.enabled = !c.enabled;
            ConfigManager.save();
            if (!c.enabled) HighlightStore.clear();
            b.setMessage(enableLabel());
        }).bounds(cx - w / 2, y, w, h).build());
        y += gap + 6;

        addRenderableWidget(Button.builder(Component.literal("Patterns & Highlight Settings"),
                b -> minecraft.setScreen(new PatternsScreen(this)))
                .bounds(cx - w / 2, y, w, h).build());
        y += gap;

        addRenderableWidget(Button.builder(Component.literal("Match & Display Settings"),
                b -> minecraft.setScreen(new SettingsScreen(this)))
                .bounds(cx - w / 2, y, w, h).build());
        y += gap;

        addRenderableWidget(Button.builder(Component.literal("Load Schematic (.litematic / .nbt)"),
                b -> minecraft.setScreen(new SchematicLoadScreen(this)))
                .bounds(cx - w / 2, y, w, h).build());
        y += gap + 8;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 50, y, 100, h).build());
    }

    private Component enableLabel() {
        return Component.literal("Mod: " + (ConfigManager.get().enabled ? "§aON" : "§cOFF")
                + " §7(click to toggle)");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, this.height / 2 - 92, 0xFFFFFFFF);
        String sub = "§7Loads patterns from Litematica .litematic / .nbt files. Scans live like X-ray.";
        g.text(this.font, sub, this.width / 2 - this.font.width(sub) / 2, this.height / 2 - 80, 0xFFA0A0A0);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }
}
