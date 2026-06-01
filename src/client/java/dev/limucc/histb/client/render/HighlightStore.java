package dev.limucc.histb.client.render;

import dev.limucc.histb.client.config.ConfigManager;
import dev.limucc.histb.client.scan.Match;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the matches currently being highlighted in-world, with an optional
 * expiry. Written by the scan callback (client thread), read by the renderer.
 */
public final class HighlightStore {

    private HighlightStore() {}

    private static final CopyOnWriteArrayList<Match> ACTIVE = new CopyOnWriteArrayList<>();
    private static volatile long expiresAtMs = 0L;

    public static void set(List<Match> matches) {
        ACTIVE.clear();
        ACTIVE.addAll(matches);
        int secs = ConfigManager.get().highlightSeconds;
        expiresAtMs = (secs <= 0) ? Long.MAX_VALUE : System.currentTimeMillis() + secs * 1000L;
    }

    public static void clear() { ACTIVE.clear(); }

    public static List<Match> get() {
        if (System.currentTimeMillis() > expiresAtMs) { ACTIVE.clear(); }
        return ACTIVE;
    }
}
