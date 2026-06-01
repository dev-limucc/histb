package dev.limucc.histb.client.config;

import java.util.ArrayList;
import java.util.List;

/** All HISTB settings + saved patterns. Persisted as JSON. */
public class ModConfig {

    public boolean enabled = true;
    public ScanMode scanMode = ScanMode.MANUAL;
    public int scanRadius = 64;
    public int autoIntervalMs = 1500;
    public int autoMinMove = 8;

    public boolean rotateY = true;
    public boolean rotateX = false;
    public boolean rotateZ = false;
    public boolean mirror  = true;

    public MatchMode matchMode = MatchMode.IGNORE_AIR;

    public boolean chatCoords = true;
    public boolean showHud    = true;

    public int maxMatches = 50;

    // ── Highlight rendering (fully customizable) ──────────────────────────────
    public boolean showBoxes = true;
    /** LINES = wireframe outline, FILLED = translucent sides, BOTH = sides + outline. */
    public HighlightStyle highlightStyle = HighlightStyle.BOTH;
    /** Box color as 0xRRGGBB. */
    public int boxColor = 0x00E0FF;     // cyan
    /** Outline opacity 0–1 (lines). */
    public float outlineAlpha = 1.0f;
    /** Fill opacity 0–1 (sides). */
    public float fillAlpha = 0.18f;
    /** Outline line width. */
    public float lineWidth = 2.0f;
    /** Render through walls (no depth test). */
    public boolean throughWalls = true;
    /** How long matches stay highlighted after a scan, in seconds (0 = until next scan). */
    public int highlightSeconds = 30;
    /** Expand the box slightly beyond the blocks so it's easier to see. */
    public float boxExpand = 0.02f;

    public List<SavedPattern> patterns = new ArrayList<>();

    public enum ScanMode { MANUAL, AUTO }
    public enum MatchMode { EXACT, BLOCK_ONLY, IGNORE_AIR }
    public enum HighlightStyle { LINES, FILLED, BOTH }

    public static class SavedPattern {
        public String name = "pattern";
        public boolean active = true;
        public int sizeX = 1, sizeY = 1, sizeZ = 1;
        public List<String> cells = new ArrayList<>();
    }
}
