package dev.limucc.histb.client.gui;

import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Patterns manager: a scrollable list of saved patterns, each with a checkbox
 * (active on/off), a name + size label, and a Delete button. Vanilla widgets so
 * all text renders. Opened via the O keybind.
 */
public class PatternsScreen extends Screen {

    private final Screen parent;
    private int scroll = 0;
    private int panelLeft, panelRight, listTop, listBottom;
    private static final int ROW_H = 22;

    public PatternsScreen(Screen parent) {
        super(Component.literal("Haven't I Seen This Before? — Patterns"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        panelLeft = cx - 170; panelRight = cx + 170;
        listTop = 56; listBottom = this.height - 56;

        // Match strictness + rotation toggles row
        ModConfig cfg = ConfigManager.get();
        int by = 28, bw = 100, gap = 6;
        addRenderableWidget(Button.builder(strictLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.matchMode = switch (c.matchMode) {
                case EXACT -> ModConfig.MatchMode.BLOCK_ONLY;
                case BLOCK_ONLY -> ModConfig.MatchMode.IGNORE_AIR;
                case IGNORE_AIR -> ModConfig.MatchMode.EXACT;
            };
            ConfigManager.save(); b.setMessage(strictLabel());
        }).bounds(panelLeft, by, bw + 40, 20).build());

        addRenderableWidget(Button.builder(rotLabel(), b -> {
            ModConfig c = ConfigManager.get();
            // cycle: Y → Y+mirror → all axes+mirror → none → Y
            if (c.rotateY && !c.mirror && !c.rotateX) { c.mirror = true; }
            else if (c.rotateY && c.mirror && !c.rotateX) { c.rotateX = true; c.rotateZ = true; }
            else if (c.rotateX) { c.rotateY = false; c.rotateX = false; c.rotateZ = false; c.mirror = false; }
            else { c.rotateY = true; c.mirror = false; c.rotateX = false; c.rotateZ = false; }
            ConfigManager.save(); b.setMessage(rotLabel());
        }).bounds(panelLeft + bw + 40 + gap, by, bw + 40, 20).build());

        // Second row: highlight controls
        int by2 = by + 24;
        addRenderableWidget(Button.builder(boxesLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.showBoxes = !c.showBoxes;
            ConfigManager.save(); b.setMessage(boxesLabel());
        }).bounds(panelLeft, by2, 90, 20).build());

        addRenderableWidget(Button.builder(styleLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.highlightStyle = switch (c.highlightStyle) {
                case LINES  -> ModConfig.HighlightStyle.FILLED;
                case FILLED -> ModConfig.HighlightStyle.BOTH;
                case BOTH   -> ModConfig.HighlightStyle.LINES;
            };
            ConfigManager.save(); b.setMessage(styleLabel());
        }).bounds(panelLeft + 96, by2, 110, 20).build());

        addRenderableWidget(Button.builder(wallsLabel(), b -> {
            ModConfig c = ConfigManager.get();
            c.throughWalls = !c.throughWalls;
            ConfigManager.save(); b.setMessage(wallsLabel());
        }).bounds(panelLeft + 212, by2, 118, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 50, this.height - 26, 100, 20).build());

        this.listTop = by2 + 28; // push the list below both control rows
    }

    private Component boxesLabel() { return Component.literal("Boxes: " + onOff(ConfigManager.get().showBoxes)); }
    private Component wallsLabel() { return Component.literal("Thru walls: " + onOff(ConfigManager.get().throughWalls)); }
    private Component styleLabel() {
        String s = switch (ConfigManager.get().highlightStyle) {
            case LINES -> "LINES"; case FILLED -> "FILLED"; case BOTH -> "BOTH";
        };
        return Component.literal("Style: " + s);
    }
    private static String onOff(boolean b) { return b ? "§aON" : "§cOFF"; }

    private Component strictLabel() {
        String s = switch (ConfigManager.get().matchMode) {
            case EXACT -> "EXACT (block+state)";
            case BLOCK_ONLY -> "BLOCK TYPE ONLY";
            case IGNORE_AIR -> "IGNORE AIR";
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 8, 0xFFFFFFFF);

        var patterns = ConfigManager.get().patterns;
        if (patterns.isEmpty()) {
            String hint = "No patterns yet — select two corners ([ and ]) and press K to capture.";
            g.text(this.font, hint, panelLeft, listTop + 4, 0xFFA0A0A0);
            return;
        }

        g.fill(panelLeft - 2, listTop - 2, panelRight + 2, listBottom + 2, 0x90000000);
        g.enableScissor(panelLeft - 2, listTop, panelRight + 2, listBottom);
        int first = Math.max(0, scroll / ROW_H);
        int visible = (listBottom - listTop) / ROW_H + 2;
        for (int i = first; i < Math.min(patterns.size(), first + visible); i++) {
            ModConfig.SavedPattern sp = patterns.get(i);
            int y = listTop + i * ROW_H - scroll;

            // checkbox
            int cbx = panelLeft + 4, cby = y + 4;
            g.fill(cbx, cby, cbx + 12, cby + 12, 0xFF808080);
            g.fill(cbx + 1, cby + 1, cbx + 11, cby + 11, 0xFF202020);
            if (sp.active) g.fill(cbx + 3, cby + 3, cbx + 9, cby + 9, 0xFF55FF55);

            String label = (sp.active ? "§a" : "§7") + sp.name
                    + " §8(" + sp.sizeX + "×" + sp.sizeY + "×" + sp.sizeZ + ")";
            g.text(this.font, label, panelLeft + 22, y + 6, 0xFFFFFFFF);

            String del = "§c[delete]";
            g.text(this.font, del, panelRight - this.font.width(del) - 6, y + 6, 0xFFFF6060);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && mx >= panelLeft && mx <= panelRight && my >= listTop && my < listBottom) {
            int idx = (int) ((my - listTop + scroll) / ROW_H);
            var patterns = ConfigManager.get().patterns;
            if (idx >= 0 && idx < patterns.size()) {
                int delX = panelRight - this.font.width("§c[delete]") - 6;
                if (mx >= delX) {
                    patterns.remove(idx);
                } else {
                    patterns.get(idx).active = !patterns.get(idx).active;
                }
                ConfigManager.save();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        var patterns = ConfigManager.get().patterns;
        int total = patterns.size() * ROW_H;
        int view = listBottom - listTop;
        int max = Math.max(0, total - view);
        scroll = Math.max(0, Math.min(max, scroll - (int) (sy * ROW_H)));
        return true;
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        this.minecraft.setScreen(this.parent);
    }
}
