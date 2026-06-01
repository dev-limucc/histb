package dev.limucc.histb.client.render;

import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.config.ModConfig;
import dev.limucc.histb.client.scan.Match;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Draws highlight boxes around active matches, using MaLiLib's RenderUtils
 * (the same renderer Litematica uses — it sets up its own camera/matrix, so we
 * just register on Fabric's stable AFTER_TRANSLUCENT_TERRAIN level event).
 *
 * Fully customizable via config: style (LINES / FILLED / BOTH), color, outline
 * alpha, fill alpha, line width, expand, through-walls, and lifetime.
 */
public final class HighlightRenderer {

    private HighlightRenderer() {}

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> render());
    }

    private static void render() {
        ModConfig cfg = ConfigManager.get();
        if (!cfg.showBoxes) return;
        List<Match> matches = HighlightStore.get();
        if (matches.isEmpty()) return;

        Color4f outline = Color4f.fromColor(cfg.boxColor, clamp(cfg.outlineAlpha));
        Color4f fill    = Color4f.fromColor(cfg.boxColor, clamp(cfg.fillAlpha));
        float expand = cfg.boxExpand;
        float lw = Math.max(1.0f, cfg.lineWidth);
        boolean through = cfg.throughWalls;

        // Render-through-walls is handled by MaLiLib's pipeline selection; the
        // overlapping/standard variants take a depth flag where available.
        for (Match m : matches) {
            BlockPos min = m.min();
            BlockPos max = m.max();
            boolean single = min.equals(max);

            boolean drawFill  = cfg.highlightStyle != ModConfig.HighlightStyle.LINES;
            boolean drawLines = cfg.highlightStyle != ModConfig.HighlightStyle.FILLED;

            if (single) {
                if (drawLines) RenderUtils.renderBlockOutline(min, expand, lw, outline, through);
            } else {
                if (drawFill)  RenderUtils.renderAreaSides(min, max, fill, through);
                if (drawLines) RenderUtils.renderAreaOutline(min, max, lw, outline, outline, outline);
            }
        }
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
