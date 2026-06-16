package com.emowolf.scm.client;

import com.emowolf.scm.SCM;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 客户端管理器：在目标位置渲染红色信标光束
 * 当天基炮光矛模式发射时，由服务端网络包触发，在目标坐标显示类似原版信标的红色光束。
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class LightSpearBeamManager {

    /** 当前活跃的光束列表 */
    private static final List<BeamInstance> activeBeams = new ArrayList<>();

    /**
     * 添加一个光束到渲染列表。由 LightSpearBeamPacket 客户端处理调用。
     * @param targetX 目标 X 坐标
     * @param targetZ 目标 Z 坐标
     * @param duration 光束持续时间（tick）
     */
    public static void addBeam(int targetX, int targetZ, int duration) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long startTime = mc.level.getGameTime();
        long endTime = startTime + duration;
        activeBeams.add(new BeamInstance(targetX, targetZ, startTime, endTime, duration));
    }

    /**
     * 客户端每 tick 清理过期光束
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            activeBeams.clear();
            return;
        }
        long gameTime = mc.level.getGameTime();
        activeBeams.removeIf(beam -> gameTime >= beam.endTime);
    }

    /**
     * 在世界渲染阶段绘制红色信标光束
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (activeBeams.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        long gameTime = mc.level.getGameTime();
        float partialTick = event.getPartialTick();
        int minY = mc.level.getMinBuildHeight();
        int beamHeight = mc.level.getMaxBuildHeight() - minY;

        for (Iterator<BeamInstance> it = activeBeams.iterator(); it.hasNext(); ) {
            BeamInstance beam = it.next();
            if (gameTime >= beam.endTime) {
                it.remove();
                continue;
            }
            // 计算透明度：开头淡入，结尾淡出
            float age = (gameTime - beam.startTime + partialTick) / (float) beam.duration;
            float alpha;
            if (age < 0.1f) {
                alpha = age / 0.1f;
            } else if (age > 0.9f) {
                alpha = (1f - age) / 0.1f;
            } else {
                alpha = 1f;
            }
            if (alpha <= 0f) continue;

            // 应用透明度到颜色
            float[] color = {
                BeamRenderUtil.COLOR_RED[0] * alpha,
                BeamRenderUtil.COLOR_RED[1] * alpha,
                BeamRenderUtil.COLOR_RED[2] * alpha
            };

            poseStack.pushPose();
            poseStack.translate(
                    beam.x + 0.5 - camPos.x,
                    minY - camPos.y,
                    beam.z + 0.5 - camPos.z
            );
            BeamRenderUtil.renderBeam(
                    poseStack, bufferSource,
                    partialTick, gameTime,
                    beamHeight,
                    color
            );
            poseStack.popPose();
        }
        bufferSource.endBatch();
    }

    /**
     * 光束实例数据
     */
    private static class BeamInstance {
        final int x;
        final int z;
        final long startTime;
        final long endTime;
        final int duration;

        BeamInstance(int x, int z, long startTime, long endTime, int duration) {
            this.x = x;
            this.z = z;
            this.startTime = startTime;
            this.endTime = endTime;
            this.duration = duration;
        }
    }
}
