package dev.limucc.histb.client.gui;

import dev.limucc.histb.client.HistbClient;
import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.schematic.SchematicIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Lists .litematic / .nbt files from the common schematic folders and loads a
 * chosen one as an active pattern. Also lets you export captured patterns.
 */
public class SchematicLoadScreen extends Screen {

    private final Screen parent;
    private final List<Path> files = new ArrayList<>();
    private int scroll = 0;
    private int panelLeft, panelRight, listTop, listBottom;
    private static final int ROW_H = 18;
    private String status = "";

    public SchematicLoadScreen(Screen parent) {
        super(Component.literal("HISTB? — Load Schematic"));
        this.parent = parent;
    }

    private static List<Path> searchDirs() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        List<Path> dirs = new ArrayList<>();
        dirs.add(gameDir.resolve("schematics"));          // Litematica default
        dirs.add(gameDir.resolve("config").resolve("histb").resolve("schematics"));
        dirs.add(gameDir.resolve("structures"));
        return dirs;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        panelLeft = cx - 180; panelRight = cx + 180;
        listTop = 50; listBottom = this.height - 56;

        files.clear();
        for (Path dir : searchDirs()) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> s = Files.walk(dir, 2)) {
                s.filter(Files::isRegularFile)
                 .filter(p -> { String n = p.getFileName().toString().toLowerCase(); return n.endsWith(".litematic") || n.endsWith(".nbt"); })
                 .forEach(files::add);
            } catch (Exception ignored) {}
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));

        addRenderableWidget(Button.builder(Component.literal("Export active patterns → .litematic"), b -> exportAll())
                .bounds(cx - 180, this.height - 28, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx + 50, this.height - 28, 100, 20).build());
    }

    private void exportAll() {
        Path outDir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        int n = 0;
        for (ModConfig.SavedPattern sp : ConfigManager.get().patterns) {
            try { SchematicIO.exportLitematic(sp, outDir.resolve(sp.name + ".litematic")); n++; }
            catch (Exception e) { HistbClient.LOGGER.error("Export failed for {}", sp.name, e); }
        }
        status = "§aExported " + n + " pattern(s) to " + outDir;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int tw = this.font.width(this.title);
        g.text(this.font, this.title, this.width / 2 - tw / 2, 14, 0xFFFFFFFF);

        if (files.isEmpty()) {
            g.text(this.font, "§7No .litematic/.nbt files found in 'schematics' or 'structures'.",
                    panelLeft, listTop + 4, 0xFFA0A0A0);
        } else {
            g.text(this.font, "§7Click a file to load it as an active pattern:", panelLeft, listTop - 12, 0xFFA0A0A0);
            g.fill(panelLeft - 2, listTop - 2, panelRight + 2, listBottom + 2, 0x90000000);
            g.enableScissor(panelLeft - 2, listTop, panelRight + 2, listBottom);
            int first = Math.max(0, scroll / ROW_H);
            int visible = (listBottom - listTop) / ROW_H + 2;
            for (int i = first; i < Math.min(files.size(), first + visible); i++) {
                int y = listTop + i * ROW_H - scroll;
                boolean hover = mouseX >= panelLeft && mouseX <= panelRight && mouseY >= y && mouseY < y + ROW_H && mouseY >= listTop && mouseY < listBottom;
                if (hover) g.fill(panelLeft, y, panelRight, y + ROW_H, 0x22FFFFFF);
                String fn = files.get(i).getFileName().toString();
                g.text(this.font, fn, panelLeft + 4, y + 5, 0xFFFFFFFF);
            }
            g.disableScissor();
        }

        if (!status.isEmpty()) g.text(this.font, status, panelLeft, this.height - 44, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() == 0 && mx >= panelLeft && mx <= panelRight && my >= listTop && my < listBottom) {
            int idx = (int) ((my - listTop + scroll) / ROW_H);
            if (idx >= 0 && idx < files.size()) {
                Path f = files.get(idx);
                try {
                    ModConfig.SavedPattern sp = SchematicIO.load(f);
                    ConfigManager.get().patterns.add(sp);
                    ConfigManager.save();
                    status = "§aLoaded §f" + sp.name + " §7(" + sp.sizeX + "×" + sp.sizeY + "×" + sp.sizeZ + ") — active";
                } catch (Exception e) {
                    status = "§cFailed: " + e.getMessage();
                    HistbClient.LOGGER.error("Load failed for {}", f, e);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int total = files.size() * ROW_H, view = listBottom - listTop;
        scroll = Math.max(0, Math.min(Math.max(0, total - view), scroll - (int) (sy * ROW_H)));
        return true;
    }

    @Override
    public void onClose() { this.minecraft.setScreen(this.parent); }
}
