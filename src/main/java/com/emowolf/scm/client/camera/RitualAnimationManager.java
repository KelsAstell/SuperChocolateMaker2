package com.emowolf.scm.client.camera;

import com.emowolf.scm.SCM;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * 献祭仪式相机动画管理器
 * <p>
 * 收到网络包后启动 ~6秒的第三人称轨道相机动画，
 * 期间播放粒子特效，动画结束后恢复第一人称视角。
 * <p>
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, value = Dist.CLIENT)
public class RitualAnimationManager {

    private static final int DURATION = 120; // 6 seconds at 20 TPS

    private static boolean active = false;
    private static int tick = 0;
    private static Vec3 orbitCenter;
    private static CameraType previousCameraType;
    private static BlockPos altarPos;

    /** 由网络包调用，启动动画 */
    public static void startAnimation(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (active) {
            // Already playing, restart
            tick = 0;
            orbitCenter = mc.player.position();
            altarPos = pos;
            return;
        }

        active = true;
        tick = 0;
        orbitCenter = mc.player.position();
        altarPos = pos;
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stopAnimation() {
        active = false;
        tick = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    // ===== Client Tick: update animation state & spawn particles =====

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!active) return;
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stopAnimation();
            return;
        }

        tick++;
        spawnAnimationParticles(mc);
        playAnimationSounds(mc);

        if (tick >= DURATION) {
            stopAnimation();
        }
    }

    // ===== Camera override: orbit around player =====

    @SubscribeEvent
    public static void onCameraSetup(ViewportEvent.ComputeCameraAngles event) {
        if (!active) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Camera camera = event.getCamera();
        float progress = tick / (float) DURATION;

        double radius;
        double heightOffset;
        double orbitAngle;

        if (progress < 0.33f) {
            // Phase 1 (0-2s): Camera pulls back & up from player position
            float p1 = progress / 0.33f;
            // Ease out cubic
            float ease = 1.0f - (1.0f - p1) * (1.0f - p1) * (1.0f - p1);
            radius = 2.0 + ease * 7.0;      // 2 -> 9
            heightOffset = 1.5 + ease * 4.5; // 1.5 -> 6
            orbitAngle = ease * Math.PI * 0.3; // slow rotation start
        } else if (progress < 0.75f) {
            // Phase 2 (2-4.5s): Full orbit around player with gentle up-down
            float p2 = (progress - 0.33f) / 0.42f;
            radius = 9.0;
            heightOffset = 6.0 + Math.sin(p2 * Math.PI * 2) * 2.5;
            orbitAngle = (0.3 + p2 * 2.2) * Math.PI; // ~1.1 full rotations
        } else {
            // Phase 3 (4.5-6s): Camera returns to front, descends slowly
            float p3 = (progress - 0.75f) / 0.25f;
            // Ease in out
            float ease = p3 < 0.5f
                    ? 2.0f * p3 * p3
                    : 1.0f - (float) Math.pow(-2.0f * p3 + 2.0f, 2) / 2.0f;
            radius = 9.0 + (2.0 - 9.0) * ease;
            heightOffset = 6.0 + (1.5 - 6.0) * ease;
            orbitAngle = (2.5 + ease * 0.5) * Math.PI;
        }

        double camX = orbitCenter.x + radius * Math.cos(orbitAngle);
        double camZ = orbitCenter.z + radius * Math.sin(orbitAngle);
        double camY = orbitCenter.y + heightOffset;

        // Use reflection to set camera position (protected in 1.20.1)
        setCameraPosition(camera, camX, camY, camZ);

        // 视角旋转效果已取消：不再根据相机位置计算 yaw/pitch 强制覆盖玩家视角方向
    }

    // ===== Particle effects =====

    private static void spawnAnimationParticles(Minecraft mc) {
        if (mc.level == null) return;
        float progress = tick / (float) DURATION;

        // --- Phase 1: Pulling particles from altar to player ---
        if (tick <= 40 && altarPos != null) {
            for (int i = 0; i < 3; i++) {
                double t = (tick + i * 3) / 40.0;
                double ax = altarPos.getX() + 0.5 + (mc.level.random.nextDouble() - 0.5) * 2.0;
                double ay = altarPos.getY() + 1.0 + mc.level.random.nextDouble() * 2.0;
                double az = altarPos.getZ() + 0.5 + (mc.level.random.nextDouble() - 0.5) * 2.0;
                double px = orbitCenter.x + (ax - orbitCenter.x) * (1.0 - t);
                double py = orbitCenter.y + 1.0 + (ay - (orbitCenter.y + 1.0)) * (1.0 - t);
                double pz = orbitCenter.z + (az - orbitCenter.z) * (1.0 - t);
                mc.level.addParticle(ParticleTypes.ENCHANT, px, py, pz, 0, 0.01, 0);
            }
        }

        // --- Phase 2: Spiral particles rising around player ---
        if (tick > 20 && tick <= 110) {
            float p2 = Math.min(1.0f, (tick - 20) / 80.0f);
            int count = 2 + (int) (p2 * 4);
            for (int i = 0; i < count; i++) {
                double angle = (tick * 0.15 + i * Math.PI * 2 / count) % (Math.PI * 2);
                double spiralRadius = 1.5 + p2 * 0.5;
                double y = orbitCenter.y + (tick - 20) * 0.08;
                double x = orbitCenter.x + Math.cos(angle) * spiralRadius;
                double z = orbitCenter.z + Math.sin(angle) * spiralRadius;
                mc.level.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0.05, 0);
            }
        }

        // --- Phase 2-3: Glow particles orbiting horizontally ---
        if (tick > 15 && tick < 115) {
            for (int i = 0; i < 2; i++) {
                double angle = (tick * 0.1 + i * Math.PI) % (Math.PI * 2);
                double r = 2.0 + Math.sin(tick * 0.05) * 0.5;
                double x = orbitCenter.x + Math.cos(angle) * r;
                double y = orbitCenter.y + 1.5 + Math.sin(tick * 0.08 + i) * 1.5;
                double z = orbitCenter.z + Math.sin(angle) * r;
                mc.level.addParticle(ParticleTypes.GLOW, x, y, z, 0, 0, 0);
            }
        }

        // --- Phase 2-3: Ambient sparkles rising from player ---
        if (tick > 10 && tick < 115) {
            if (mc.level.random.nextInt(3) == 0) {
                double x = orbitCenter.x + (mc.level.random.nextDouble() - 0.5) * 1.0;
                double y = orbitCenter.y + 0.5;
                double z = orbitCenter.z + (mc.level.random.nextDouble() - 0.5) * 1.0;
                mc.level.addParticle(ParticleTypes.FIREWORK,
                        x, y, z,
                        (mc.level.random.nextDouble() - 0.5) * 0.02,
                        0.08 + mc.level.random.nextDouble() * 0.04,
                        (mc.level.random.nextDouble() - 0.5) * 0.02);
            }
        }

        // --- Phase 3: Grand burst at the end ---
        if (tick >= 105 && tick < 120) {
            float burstProgress = (tick - 105) / 15.0f;
            int burstCount = 8 + (int) (burstProgress * 12);
            for (int i = 0; i < burstCount; i++) {
                double angle = mc.level.random.nextDouble() * Math.PI * 2;
                double phi = mc.level.random.nextDouble() * Math.PI * 2;
                double speed = 0.05 + mc.level.random.nextDouble() * 0.2;
                double vx = Math.cos(angle) * Math.cos(phi) * speed;
                double vy = Math.sin(phi) * speed + 0.05;
                double vz = Math.sin(angle) * Math.cos(phi) * speed;
                mc.level.addParticle(
                        mc.level.random.nextBoolean() ? ParticleTypes.END_ROD : ParticleTypes.GLOW,
                        orbitCenter.x, orbitCenter.y + 1.0, orbitCenter.z,
                        vx, vy, vz);
            }
        }
    }

    // ===== Sound effects =====

    private static void playAnimationSounds(Minecraft mc) {
        if (mc.level == null) return;

        // Phase 1 start: low rumble
        if (tick == 1) {
            mc.level.playLocalSound(orbitCenter.x, orbitCenter.y, orbitCenter.z,
                    SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS,
                    1.2f, 0.6f, false);
        }

        // Phase 2: wind/hum during orbit
        if (tick == 45) {
            mc.level.playLocalSound(orbitCenter.x, orbitCenter.y, orbitCenter.z,
                    SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.BLOCKS,
                    0.4f, 1.8f, false);
        }

        // Phase 3 climax: powerful burst
        if (tick == 105) {
            mc.level.playLocalSound(orbitCenter.x, orbitCenter.y, orbitCenter.z,
                    SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS,
                    1.0f, 1.0f, false);
        }

        // Phase 3: level up chime
        if (tick == 115) {
            mc.level.playLocalSound(orbitCenter.x, orbitCenter.y, orbitCenter.z,
                    SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS,
                    1.0f, 1.2f, false);
        }
    }

    // ===== Reflection helper for protected Camera.setPosition =====

    private static Method cameraSetPositionMethod;

    private static void setCameraPosition(Camera camera, double x, double y, double z) {
        try {
            if (cameraSetPositionMethod == null) {
                cameraSetPositionMethod = Camera.class.getDeclaredMethod("setPosition",
                        double.class, double.class, double.class);
                cameraSetPositionMethod.setAccessible(true);
            }
            cameraSetPositionMethod.invoke(camera, x, y, z);
        } catch (Exception e) {
            // Silently ignore - camera position won't be modified
        }
    }
}
