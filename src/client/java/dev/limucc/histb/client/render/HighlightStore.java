package dev.limucc.histb.client.render;

import dev.limucc.histb.client.scan.Match;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the matches currently highlighted in-world. Replaced wholesale each time
 * the continuous scanner finishes a pass (live, X-ray style). Written on the
 * client thread, read by the renderer.
 */
public final class HighlightStore {

    private HighlightStore() {}

    private static final CopyOnWriteArrayList<Match> ACTIVE = new CopyOnWriteArrayList<>();

    public static void set(List<Match> matches) {
        ACTIVE.clear();
        ACTIVE.addAll(matches);
    }

    public static void clear() {
        if (!ACTIVE.isEmpty()) ACTIVE.clear();
    }

    public static List<Match> get() { return ACTIVE; }
}
