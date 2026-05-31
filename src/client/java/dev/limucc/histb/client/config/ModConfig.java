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
    public boolean showBoxes  = true;

    public int maxMatches = 50;

    public List<SavedPattern> patterns = new ArrayList<>();

    public enum ScanMode { MANUAL, AUTO }
    public enum MatchMode { EXACT, BLOCK_ONLY, IGNORE_AIR }

    public static class SavedPattern {
        public String name = "pattern";
        public boolean active = true;
        public int sizeX = 1, sizeY = 1, sizeZ = 1;
        public List<String> cells = new ArrayList<>();
    }
}
