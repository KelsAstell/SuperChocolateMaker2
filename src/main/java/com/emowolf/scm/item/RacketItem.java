package com.emowolf.scm.item;

import com.emowolf.scm.SCM;
import com.emowolf.scm.network.SCMNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 球拍 — 能量驱动的弹射物反弹武器。
 * <ul>
 *   <li>内部储存 409600 FE 能量</li>
 *   <li>可通过充电或吸收巧克力/电池补充能量</li>
 *   <li>左键挥拍：弹飞面前 10 格内的所有弹射物（类似反弹恶魂火球），消耗 100 FE</li>
 * </ul>
 */
public class RacketItem extends Item {

    /** 能量储存上限（FE） */
    public static final int MAX_ENERGY = 409600;
    /** 每次挥拍消耗（FE） */
    public static final int COST_PER_SWING = 100;
    /** 反弹范围（格） */
    public static final double DEFLECT_RANGE = 24.0;
    /** 反弹速度倍率（基于原速度的倍数） */
    public static final double DEFLECT_SPEED_MULTIPLIER = 2.0;
    /** 反弹最低速度 */
    public static final double DEFLECT_MIN_SPEED = 2.0;

    private static final String TAG_ENERGY = "Energy";

    // ==================== 构造 ====================

    public RacketItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ==================== 能量管理 ====================

    public static long getEnergy(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(TAG_ENERGY)) {
            return tag.getLong(TAG_ENERGY);
        }
        return 0;
    }

    public static void addEnergy(ItemStack stack, long amount) {
        CompoundTag tag = stack.getOrCreateTag();
        long current = getEnergy(stack);
        tag.putLong(TAG_ENERGY, Math.min(MAX_ENERGY, Math.max(0, current + amount)));
    }

    // ==================== 显示 ====================

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getEnergy(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long energy = getEnergy(stack);
        if (energy <= 0) return 0;
        return Math.min(13, (int) (energy * 13.0 / MAX_ENERGY));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x55CCFF; // 浅蓝色
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltip, isAdvanced);
        tooltip.add(Component.translatable("item.chocomaker.racket.desc"));
        tooltip.add(Component.translatable("item.chocomaker.racket.energy", getEnergy(stack)));
    }

    // ==================== Forge Energy Capability ====================

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {
            private final LazyOptional<IEnergyStorage> holder = LazyOptional.of(() -> new IEnergyStorage() {
                @Override
                public int receiveEnergy(int maxReceive, boolean simulate) {
                    long current = getEnergy(stack);
                    long space = MAX_ENERGY - current;
                    if (space <= 0) return 0;
                    int toReceive = (int) Math.min(maxReceive, space);
                    if (!simulate) {
                        addEnergy(stack, toReceive);
                    }
                    return toReceive;
                }

                @Override
                public int extractEnergy(int maxExtract, boolean simulate) {
                    return 0;
                }

                @Override
                public int getEnergyStored() {
                    return (int) Math.min(getEnergy(stack), Integer.MAX_VALUE);
                }

                @Override
                public int getMaxEnergyStored() {
                    return MAX_ENERGY;
                }

                @Override
                public boolean canExtract() {
                    return false;
                }

                @Override
                public boolean canReceive() {
                    return getEnergy(stack) < MAX_ENERGY;
                }
            });

            @Override
            public <T> LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
                if (cap == ForgeCapabilities.ENERGY) return holder.cast();
                return LazyOptional.empty();
            }
        };
    }

    // ==================== 反弹逻辑 ====================

    /**
     * 服务端：弹飞玩家面前的所有弹射物，将其方向改为玩家面朝方向。
     * @return 实际反弹的弹射物数量
     */
    public static int deflectProjectiles(Level level, Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        Vec3 end = eyePos.add(look.scale(DEFLECT_RANGE));

        AABB searchBox = new AABB(eyePos, end).inflate(2.0);

        List<Projectile> projectiles = level.getEntitiesOfClass(Projectile.class, searchBox,
                p -> p.isAlive() && !p.is(player));

        if (projectiles.isEmpty()) return 0;

        int count = 0;
        for (Projectile projectile : projectiles) {
            double speed = projectile.getDeltaMovement().length();
            double reboundSpeed = Math.max(speed * DEFLECT_SPEED_MULTIPLIER, DEFLECT_MIN_SPEED);
            projectile.setDeltaMovement(look.scale(reboundSpeed));
            projectile.hasImpulse = true;
            count++;
        }

        // 播放反弹音效
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.5f);

        return count;
    }

    // ==================== 事件处理器 ====================

    @Mod.EventBusSubscriber(modid = SCM.MODID)
    public static class RacketEventHandler {

        /**
         * 客户端：检测左键挥拍，发送网络包到服务端
         */
        @Mod.EventBusSubscriber(modid = SCM.MODID, value = Dist.CLIENT)
        public static class ClientHandler {
            @SubscribeEvent
            public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
                Player player = event.getEntity();
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof RacketItem)) return;

                SCMNetwork.CHANNEL.sendToServer(new SCMNetwork.RacketSwingPacket());
            }
        }

        /**
         * 拾取巧克力/电池时自动充能 — 手持球拍时拦截拾取事件，直接转化为能量
         */
        @SubscribeEvent
        public static void onItemPickup(EntityItemPickupEvent event) {
            Player player = event.getEntity();
            if (player.level().isClientSide()) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof RacketItem)) return;

            ItemEntity itemEntity = event.getItem();
            ItemStack pickupStack = itemEntity.getItem();

            long currentEnergy = getEnergy(mainHand);
            if (currentEnergy >= MAX_ENERGY) return;

            int totalGain = SCMItems.getEnergyValueFE(pickupStack);
            if (totalGain <= 0) return;

            addEnergy(mainHand, totalGain);
            itemEntity.discard();
            event.setCanceled(true);

            long newEnergy = getEnergy(mainHand);
            if (newEnergy >= MAX_ENERGY) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("msg.chocomaker.racket.energy_full", newEnergy));
            } else {
                serverPlayer.sendSystemMessage(
                        Component.translatable("msg.chocomaker.racket.absorbed", newEnergy));
            }
        }
    }
}
