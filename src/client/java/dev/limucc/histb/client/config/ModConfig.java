package dev.limucc.histb.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * HISTB? settings + loaded patterns. Persisted as JSON.
 *
 * Design (Litematica/MiniHUD style): when enabled, it continuously scans your
 * loaded area and keeps live highlights — like X-ray. No manual scan, no radius
 * picker, no match cap, no timer. Patterns come from .litematic / .nbt files.
 */
public class ModConfig {

    /** Master on/off. Toggled by the (unbound) keybind or in the GUI. */
    public boolean enabled = false;

    // ── Orientation matching ──────────────────────────────────────────────────
    public boolean rotateY = true;   // 4 horizontal rotations
    public boolean rotateX = false;
    public boolean rotateZ = false;
    public boolean mirror  = true;

    // ── Match strictness ──────────────────────────────────────────────────────
    public MatchMode matchMode = MatchMode.IGNORE_AIR;

    // ── Output ────────────────────────────────────────────────────────────────
    public boolean chatCoords = false;  // off by default — highlights are the main feedback
    public boolean showBoxes  = true;

    // ── Highlight rendering (Litematica-like, fully customizable) ─────────────
    public HighlightStyle highlightStyle = HighlightStyle.BOTH;
    public int boxColor = 0x00E0FF;     // cyan, 0xRRGGBB
    public float outlineAlpha = 1.0f;
    public float fillAlpha = 0.18f;
    public float lineWidth = 2.0f;
    public boolean throughWalls = true;
    public float boxExpand = 0.02f;

    /** Loaded patterns (from .litematic / .nbt). Each can be toggled active. */
    public List<SavedPattern> patterns = new ArrayList<>();

    public enum MatchMode { EXACT, BLOCK_ONLY, IGNORE_AIR }
    public enum HighlightStyle { LINES, FILLED, BOTH }

    public static class SavedPattern {
        public String name = "pattern";
        public boolean active = true;
        public int sizeX = 1, sizeY = 1, sizeZ = 1;
        public List<String> cells = new ArrayList<>();
    }
}
