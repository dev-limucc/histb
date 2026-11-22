package dev.limucc.histb.client.compat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Vector4f;

/**
 * MC 26.3: MaLiLib 0.30 renders through its own RenderContext/pipelines and expects callers to draw from its
 * world-last hook (the same one Litematica / MiniHUD use) - drawing from a Fabric level event shows nothing.
 */
public final class RenderHook implements IRenderer {

    private final Runnable draw;

    private RenderHook(Runnable draw) {
        this.draw = draw;
    }

    public static void register(Runnable draw) {
        RenderEventHandler.getInstance().registerWorldLastRenderer(new RenderHook(draw));
    }

    @Override
    public void onRenderWorldLast(RenderTarget target, CameraRenderState camera, Frustum frustum, RenderBuffers buffers,
                                  GpuBufferSlice fogSlice, Vector4f fogColor, ProfilerFiller profiler) {
        draw.run();
    }

    @Override
    public java.util.function.Supplier<String> getProfilerSectionSupplier() {
        return () -> "histb_highlights";
    }
}
