package dev.limucc.histb.client.compat;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

/** MC 26.1 / 26.2: draw from Fabric's stable AFTER_TRANSLUCENT_TERRAIN level event (MaLiLib's RenderUtils sets up its own state). */
public final class RenderHook {

    private RenderHook() {}

    public static void register(Runnable draw) {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> draw.run());
    }
}
