package com.emowolf.scm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;

/**
 * 统一的信标光束渲染工具。
 * EternalBeaconRenderer 和 LightSpearBeamManager 共用此工具，
 * 确保光束参数（颜色、半径、纹理）在一处集中管理。
 *
 * 调用者需自行将 PoseStack 定位到光束起点（世界坐标），
 * 并提供合适的 MultiBufferSource。
 */
public final class BeamRenderUtil {

    // ---- 颜色预设 ----
    /** 永恒信标：巧克力金 */
    public static final float[] COLOR_GOLDEN  = {0.90f, 0.75f, 0.30f};
    /** 天基炮光矛：警示红 */
    public static final float[] COLOR_RED     = {0.90f, 0.05f, 0.05f};

    // ---- 光束几何参数 ----
    private static final float BEAM_RADIUS  = 0.15f;
    private static final float GLOW_RADIUS  = 0.25f;
    private static final float ANIM_SPEED   = 1.0f;

    private BeamRenderUtil() {}

    /**
     * 在 PoseStack 当前位置渲染一道信标光束。
     *
     * @param poseStack    已定位到光束起点的 PoseStack（调用者负责 translate）
     * @param bufferSource MultiBufferSource（若为 BufferSource，调用者负责 endBatch）
     * @param partialTick  当前帧 partialTick
     * @param gameTime     世界 gameTime
     * @param beamHeight   光束高度（从 PoseStack 原点向上延伸）
     * @param color        RGB 颜色数组，长度 3
     */
    public static void renderBeam(PoseStack poseStack,
                                  MultiBufferSource bufferSource,
                                  float partialTick,
                                  long gameTime,
                                  int beamHeight,
                                  float[] color) {
        BeaconRenderer.renderBeaconBeam(
                poseStack, bufferSource,
                BeaconRenderer.BEAM_LOCATION,
                ANIM_SPEED, partialTick, gameTime,
                0, beamHeight,
                color,
                BEAM_RADIUS, GLOW_RADIUS
        );
    }
}
