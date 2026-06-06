package dev.limucc.histb.client.gui;

import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.render.HighlightStore;
import dev.limucc.histb.client.schematic.SchematicIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Single Cloth-style tabbed config screen for HISTB?. Built with vanilla widgets
 * (Cloth's own text inputs render invisibly in 26.1) but laid out to match Cloth's
 * look: a tab row across the top, a dark content panel, and a footer.
 *
 * Tabs: General · Matching · Display · Patterns · Schematics
 */
public class HistbConfigScreen extends Screen {

    private enum Tab { GENERAL, MATCHING, DISPLAY, PATTERNS, SCHEMATICS }

    private final Screen parent;
    private Tab tab = Tab.GENERAL;

    // layout
    private int panelL, panelR, panelT, panelB, tabRowY, contentTop;

    // patterns/schematics list state
    private int listScroll = 0;
    private static final int ROW_H = 20;
    private final List<Path> schemFiles = new ArrayList<>();
    private static List<Path> SCAN_DIRS_CACHE;
    private String status = "";

    public HistbConfigScreen(Screen parent) {
        super(Component.literal("Haven't I Seen This Before?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(420, this.width - 40);
        panelL = (this.width - w) / 2;
        panelR = panelL + w;
        panelT = 30;
        panelB = this.height - 36;
        tabRowY = panelT + 8;
        contentTop = tabRowY + 26;
        rebuild();
    }

    /** Rebuilds widgets for the current tab. */
    private void rebuild() {
        this.clearWidgets();

        // ── Tab bar ──
        Tab[] tabs = Tab.values();
        String[] names = {"General", "Matching", "Display", "Patterns", "Schematics"};
        int tabW = (panelR - panelL) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            Component label = Component.literal((t == tab ? "§f§n" : "§7") + names[i]);
            Button tb = Button.builder(label, b -> { tab = t; listScroll = 0; rebuild(); })
                    .bounds(panelL + i * tabW, tabRowY, tabW - 2, 18).build();
            addRenderableWidget(tb);
        }

        // ── Footer ──
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());

        // ── Content per tab ──
        int cx = (panelL + panelR) / 2;
        int y = contentTop + 6;
        int bw = panelR - panelL - 40;
        int bx = cx - bw / 2;
        int gap = 24;

        switch (tab) {
            case GENERAL -> {
                addContentButton(bx, y, bw, this::enableLabel, () -> {
                    ModConfig c = ConfigManager.get(); c.enabled = !c.enabled;
                    if (!c.enabled) HighlightStore.clear();
                    ConfigManager.save();
                }); y += gap;
            }
            case MATCHING -> {
                addContentButton(bx, y, bw, this::strictLabel, () -> {
                    ModConfig c = ConfigManager.get();
                    c.matchMode = switch (c.matchMode) {
                        case EXACT -> ModConfig.MatchMode.BLOCK_ONLY;
                        case BLOCK_ONLY -> ModConfig.MatchMode.IGNORE_AIR;
                        case IGNORE_AIR -> ModConfig.MatchMode.EXACT;
                    };
                    ConfigManager.save();
                }); y += gap;
                addContentButton(bx, y, bw, this::rotLabel, () -> {
                    ModConfig c = ConfigManager.get();
                    if (c.rotateY && !c.mirror && !c.rotateX) c.mirror = true;
                    else if (c.rotateY && c.mirror && !c.rotateX) { c.rotateX = true; c.rotateZ = true; }
                    else if (c.rotateX) { c.rotateY = false; c.rotateX = false; c.rotateZ = false; c.mirror = false; }
                    else { c.rotateY = true; c.mirror = false; c.rotateX = false; c.rotateZ = false; }
                    ConfigManager.save();
                }); y += gap;
            }
            case DISPLAY -> {
                addContentButton(bx, y, bw, this::styleLabel, () -> {
                    ModConfig c = ConfigManager.get();
                    c.highlightStyle = switch (c.highlightStyle) {
                        case LINES -> ModConfig.HighlightStyle.FILLED;
                        case FILLED -> ModConfig.HighlightStyle.BOTH;
                        case BOTH -> ModConfig.HighlightStyle.LINES;
                    };
                    ConfigManager.save();
                }); y += gap;
                addContentButton(bx, y, bw, this::colorLabel, () -> {
                    ModConfig c = ConfigManager.get();
                    int[] pal = {0x00E0FF, 0xFF3030, 0x30FF30, 0xFFE030, 0xFF30FF, 0xFFFFFF, 0xFF8000};
                    int i = 0; for (; i < pal.length; i++) if (pal[i] == c.boxColor) break;
                    c.boxColor = pal[(i + 1) % pal.length]; ConfigManager.save();
                }); y += gap;
                addContentButton(bx, y, bw, this::wallsLabel, () -> {
                    ModConfig c = ConfigManager.get(); c.throughWalls = !c.throughWalls; ConfigManager.save();
                }); y += gap;
                addContentButton(bx, y, bw, this::chatLabel, () -> {
                    ModConfig c = ConfigManager.get(); c.chatCoords = !c.chatCoords; ConfigManager.save();
                }); y += gap;
            }
            case PATTERNS -> { /* list rendered + clicked in extractRenderState/mouseClicked */ }
            case SCHEMATICS -> {
                refreshSchemFiles();
            }
        }
    }

    private void addContentButton(int x, int y, int w, java.util.function.Supplier<Component> label, Runnable onClick) {
        Button b = Button.builder(label.get(), btn -> { onClick.run(); btn.setMessage(label.get()); })
                .bounds(x, y, w, 20).build();
        addRenderableWidget(b);
    }

    // ── labels ──
    private Component enableLabel() { return Component.literal("Mod: " + (ConfigManager.get().enabled ? "§aENABLED" : "§cDISABLED")); }
    private Component strictLabel() {
        String s = switch (ConfigManager.get().matchMode) {
            case EXACT -> "Exact (block + state)"; case BLOCK_ONLY -> "Block type only"; case IGNORE_AIR -> "Ignore air (wildcard)";
        };
        return Component.literal("Match strictness: §b" + s);
    }
    private Component rotLabel() {
        ModConfig c = ConfigManager.get();
        String s; if (!c.rotateY && !c.rotateX && !c.rotateZ && !c.mirror) s = "none";
        else if (c.rotateX || c.rotateZ) s = "all axes + mirror";
        else if (c.mirror) s = "Y + mirror"; else s = "Y rotations";
        return Component.literal("Rotations: §b" + s);
    }
    private Component styleLabel() {
        String s = switch (ConfigManager.get().highlightStyle) { case LINES -> "Lines"; case FILLED -> "Filled"; case BOTH -> "Both"; };
        return Component.literal("Highlight style: §b" + s);
    }
    private Component colorLabel() { return Component.literal(String.format("Highlight color: §b#%06X", ConfigManager.get().boxColor)); }
    private Component wallsLabel() { return Component.literal("Show through terrain: " + (ConfigManager.get().throughWalls ? "§aYes" : "§cNo")); }
    private Component chatLabel()  { return Component.literal("Print coords to chat: " + (ConfigManager.get().chatCoords ? "§aYes" : "§cNo")); }

    // ── schematics ──
    private static List<Path> scanDirs() {
        if (SCAN_DIRS_CACHE == null) {
            Path g = FabricLoader.getInstance().getGameDir();
            SCAN_DIRS_CACHE = List.of(g.resolve("schematics"), g.resolve("structures"));
        }
        return SCAN_DIRS_CACHE;
    }
    private void refreshSchemFiles() {
        schemFiles.clear();
        for (Path dir : scanDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> s = Files.walk(dir, 3)) {
                s.filter(Files::isRegularFile)
                 .filter(p -> { String n = p.getFileName().toString().toLowerCase(); return n.endsWith(".litematic") || n.endsWith(".nbt"); })
                 .forEach(schemFiles::add);
            } catch (Exception ignored) {}
        }
        schemFiles.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));
    }

    // ── render ──
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);

        // title
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 12, 0xFFFFFFFF);

        // subtle separator line under the tab row — no dark fill (keeps it light, like Cloth)
        g.fill(panelL, contentTop, panelR, contentTop + 1, 0x40FFFFFF);

        if (tab == Tab.PATTERNS)   renderPatterns(g, mouseX, mouseY);
        if (tab == Tab.SCHEMATICS) renderSchematics(g, mouseX, mouseY);
        if (tab == Tab.GENERAL) {
            g.text(this.font, "§7Schematic match finder. Load a schematic, then enable to outline matches in your loaded area.",
                    panelL + 20, contentTop + 40, 0xFFA0A0A0);
        }
        if (!status.isEmpty()) g.text(this.font, status, panelL + 20, panelB - 14, 0xFFFFFFFF);
    }

    private int listTop() { return contentTop + 8; }
    private int listBottom() { return panelB - 8; }

    private void renderPatterns(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        var patterns = ConfigManager.get().patterns;
        if (patterns.isEmpty()) {
            g.text(this.font, "§7No patterns. Open the Schematics tab to load a .litematic / .nbt.",
                    panelL + 20, contentTop + 20, 0xFFA0A0A0);
            return;
        }
        int lt = listTop(), lb = listBottom();
        g.enableScissor(panelL + 6, lt, panelR - 6, lb);
        int first = Math.max(0, listScroll / ROW_H);
        int vis = (lb - lt) / ROW_H + 2;
        for (int i = first; i < Math.min(patterns.size(), first + vis); i++) {
            ModConfig.SavedPattern sp = patterns.get(i);
            int y = lt + i * ROW_H - listScroll;
            boolean hover = mouseX >= panelL + 6 && mouseX <= panelR - 6 && mouseY >= y && mouseY < y + ROW_H && mouseY >= lt && mouseY < lb;
            if (hover) g.fill(panelL + 6, y, panelR - 6, y + ROW_H, 0x22FFFFFF);
            // checkbox
            int cbx = panelL + 12, cby = y + 4;
            g.fill(cbx, cby, cbx + 12, cby + 12, 0xFF808080);
            g.fill(cbx + 1, cby + 1, cbx + 11, cby + 11, 0xFF202020);
            if (sp.active) g.fill(cbx + 3, cby + 3, cbx + 9, cby + 9, 0xFF55FF55);
            g.text(this.font, (sp.active ? "§f" : "§7") + sp.name + " §8(" + sp.sizeX + "×" + sp.sizeY + "×" + sp.sizeZ + ")",
                    panelL + 32, y + 6, 0xFFFFFFFF);
            String del = "§c[delete]";
            g.text(this.font, del, panelR - this.font.width(del) - 12, y + 6, 0xFFFF6060);
        }
        g.disableScissor();
    }

    private void renderSchematics(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, "§7Click a file to load it as an active pattern:", panelL + 12, contentTop + 6, 0xFFA0A0A0);
        if (schemFiles.isEmpty()) {
            g.text(this.font, "§7No .litematic/.nbt found in 'schematics' or 'structures'.", panelL + 12, contentTop + 22, 0xFFA0A0A0);
            return;
        }
        int lt = listTop() + 14, lb = listBottom();
        g.enableScissor(panelL + 6, lt, panelR - 6, lb);
        int first = Math.max(0, listScroll / ROW_H);
        int vis = (lb - lt) / ROW_H + 2;
        for (int i = first; i < Math.min(schemFiles.size(), first + vis); i++) {
            int y = lt + i * ROW_H - listScroll;
            boolean hover = mouseX >= panelL + 6 && mouseX <= panelR - 6 && mouseY >= y && mouseY < y + ROW_H && mouseY >= lt && mouseY < lb;
            if (hover) g.fill(panelL + 6, y, panelR - 6, y + ROW_H, 0x22FFFFFF);
            g.text(this.font, schemFiles.get(i).getFileName().toString(), panelL + 12, y + 6, 0xFFFFFFFF);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() != 0) return false;

        if (tab == Tab.PATTERNS) {
            int lt = listTop(), lb = listBottom();
            if (mx >= panelL + 6 && mx <= panelR - 6 && my >= lt && my < lb) {
                int idx = (int) ((my - lt + listScroll) / ROW_H);
                var patterns = ConfigManager.get().patterns;
                if (idx >= 0 && idx < patterns.size()) {
                    int delX = panelR - this.font.width("§c[delete]") - 12;
                    if (mx >= delX) patterns.remove(idx);
                    else patterns.get(idx).active = !patterns.get(idx).active;
                    ConfigManager.save();
                    return true;
                }
            }
        } else if (tab == Tab.SCHEMATICS) {
            int lt = listTop() + 14, lb = listBottom();
            if (mx >= panelL + 6 && mx <= panelR - 6 && my >= lt && my < lb) {
                int idx = (int) ((my - lt + listScroll) / ROW_H);
                if (idx >= 0 && idx < schemFiles.size()) {
                    Path f = schemFiles.get(idx);
                    try {
                        ModConfig.SavedPattern sp = SchematicIO.load(f);
                        ConfigManager.get().patterns.add(sp);
                        ConfigManager.save();
                        status = "§aLoaded §f" + sp.name + " §7(" + sp.sizeX + "×" + sp.sizeY + "×" + sp.sizeZ + ") — active";
                    } catch (Exception e) {
                        status = "§cFailed: " + e.getMessage();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab == Tab.PATTERNS || tab == Tab.SCHEMATICS) {
            int count = (tab == Tab.PATTERNS) ? ConfigManager.get().patterns.size() : schemFiles.size();
            int total = count * ROW_H, view = listBottom() - listTop();
            listScroll = Math.max(0, Math.min(Math.max(0, total - view), listScroll - (int) (sy * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }
}
