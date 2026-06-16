package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 睡帽睡眠维持处理器：
 * 防止被睡帽点击的生物/玩家自动醒来。
 * 
 * 机制：
 * - 当睡帽使用时，在目标实体的NBT中设置标记 "scm_forced_sleep" = true
 * - 在tick事件中检查该标记，如果实体已醒来但标记仍存在，则重新设置睡眠姿态
 * - 实体受到伤害或玩家交互时会清除标记并正常醒来
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SleepCapHandler {

    private static final String NBT_KEY = "scm_forced_sleep";

    /**
     * 跟踪所有被强制睡眠的非玩家实体，用于 ServerTickEvent 中高效覆盖姿态。
     * 使用 WeakHashMap 包装为 Set，实体被 GC 时自动清理，避免内存泄漏。
     */
    private static final Set<LivingEntity> forcedSleepEntities =
            Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * 生物tick事件：维护被强制睡眠实体的睡眠状态。
     * 
     * LivingTickEvent 按实体触发，零额外扫描开销。
     * - 非玩家实体：维持 startSleeping() 让原版 aiStep 冻结移动。
     * - 玩家：不在睡眠中 → 判定为主动起床，清除标记。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity livingEntity = event.getEntity();
        if (livingEntity.level().isClientSide()) return;

        CompoundTag nbt = livingEntity.getPersistentData();
        if (!nbt.getBoolean(NBT_KEY)) return;

        if (livingEntity instanceof ServerPlayer player) {
            if (!player.isSleeping()) {
                nbt.remove(NBT_KEY);
            }
        } else {
            if (!livingEntity.isSleeping()) {
                livingEntity.startSleeping(livingEntity.blockPosition());
            }
        }
    }

    /**
     * 服务端 tick 末尾：强制覆盖被跟踪实体的姿态。
     * 
     * 只遍历 tracked set（通常为空或仅数个实体），而非全维度扫描，
     * 性能开销极小。WeakHashMap 确保实体被移除/死亡后自动清理。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // 使用 toArray 快照遍历，避免并发修改
        for (LivingEntity entity : forcedSleepEntities.toArray(new LivingEntity[0])) {
            if (!entity.isAlive() || entity.isRemoved()) {
                forcedSleepEntities.remove(entity);
                continue;
            }
            if (!entity.getPersistentData().getBoolean(NBT_KEY)) {
                forcedSleepEntities.remove(entity);
                continue;
            }
            if (entity.getPose() != Pose.SLEEPING) {
                entity.setPose(Pose.SLEEPING);
            }
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * 当生物受到伤害时，清除强制睡眠标记并移出跟踪集
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        CompoundTag nbt = entity.getPersistentData();
        if (nbt.getBoolean(NBT_KEY)) {
            nbt.remove(NBT_KEY);
            forcedSleepEntities.remove(entity);
            entity.stopSleeping();
        }
    }

    /**
     * 当玩家右键点击强制睡眠中的玩家时，清除标记并唤醒
     */
    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer targetPlayer)) return;
        if (targetPlayer.level().isClientSide()) return;

        CompoundTag nbt = targetPlayer.getPersistentData();
        if (nbt.getBoolean(NBT_KEY)) {
            nbt.remove(NBT_KEY);
            targetPlayer.stopSleeping();
        }
    }

    /**
     * 当玩家左键点击强制睡眠中的玩家时，清除标记并唤醒
     */
    @SubscribeEvent
    public static void onPlayerAttackEntity(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof ServerPlayer targetPlayer)) return;
        if (targetPlayer.level().isClientSide()) return;

        CompoundTag nbt = targetPlayer.getPersistentData();
        if (nbt.getBoolean(NBT_KEY)) {
            nbt.remove(NBT_KEY);
            targetPlayer.stopSleeping();
        }
    }

    /**
     * 工具方法：标记实体为强制睡眠状态。
     * 非玩家实体会被加入跟踪集，用于 ServerTickEvent 末尾姿态覆盖。
     */
    public static void markForcedSleep(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        
        CompoundTag nbt = entity.getPersistentData();
        nbt.putBoolean(NBT_KEY, true);
        
        entity.startSleeping(entity.blockPosition());

        // 非玩家实体加入跟踪集
        if (!(entity instanceof ServerPlayer)) {
            forcedSleepEntities.add(entity);
        }
    }

    /**
     * 工具方法：清除强制睡眠标记并移出跟踪集
     */
    public static void clearForcedSleep(LivingEntity entity) {
        CompoundTag nbt = entity.getPersistentData();
        nbt.remove(NBT_KEY);
        forcedSleepEntities.remove(entity);
    }
}
