package dev.limucc.histb.client.gui;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.scan.Scanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Main hub — everything is reachable from here (so no keybinds are required).
 * Opened from ModMenu and/or the optional (unbound by default) Open key.
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
        int y = this.height / 2 - 60;
        int w = 220, h = 20, gap = 24;

        addRenderableWidget(Button.builder(Component.literal("Patterns & Highlight Settings"),
                b -> minecraft.setScreen(new PatternsScreen(this)))
                .bounds(cx - w / 2, y, w, h).build());
        y += gap;

        addRenderableWidget(Button.builder(Component.literal("Scan Settings (radius, output)"),
                b -> minecraft.setScreen(new SettingsScreen(this)))
                .bounds(cx - w / 2, y, w, h).build());
        y += gap;

        addRenderableWidget(Button.builder(Component.literal("Load Schematic (.litematic / .nbt)"),
                b -> minecraft.setScreen(new SchematicLoadScreen(this)))
                .bounds(cx - w / 2, y, w, h).build());
        y += gap;

        addRenderableWidget(Button.builder(Component.literal("§aScan Now"),
                b -> { minecraft.setScreen(parent); runScan(); })
                .bounds(cx - w / 2, y, w, h).build());
        y += gap + 8;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 50, y, 100, h).build());
    }

    private void runScan() {
        Minecraft mc = this.minecraft;
        if (mc.player == null) return;
        Scanner.scanAsync((matches, truncated) -> HistbClient.reportFromHub(mc, matches, truncated));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, this.height / 2 - 88, 0xFFFFFFFF);
        String sub = "§7Capture: set corners then capture — see Patterns for keys/help";
        g.text(this.font, sub, this.width / 2 - this.font.width(sub) / 2, this.height / 2 - 76, 0xFFA0A0A0);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }
}
