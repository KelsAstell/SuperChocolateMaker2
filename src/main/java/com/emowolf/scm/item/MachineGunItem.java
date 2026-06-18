package com.emowolf.scm.item;

import com.emowolf.scm.SCM;
import com.emowolf.scm.network.SCMNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * 可可机枪 — 以巧克力为能源的光束狙击武器。
 * <ul>
 *   <li>潜行即开镜（高倍望远镜），无需右键</li>
 *   <li>潜行期间持续消耗能量，右键发射光束</li>
 *   <li>光束对沿途所有生物造成伤害，伤害与潜行消耗能量成正比</li>
 *   <li>非潜行右键：单发速射箭矢</li>
 *   <li>手持时拾取巧克力/巧克力电池自动转化为内部能量</li>
 * </ul>
 */
public class MachineGunItem extends Item {

    // ==================== 常量 ====================

    /** 能量储存上限（FE） */
    public static final int MAX_ENERGY = 81920000;
    /** 非潜行单发能量消耗 */
    public static final int NORMAL_SHOT_COST = 100;
    /** 连射间隔（tick） */
    public static final int RAPID_FIRE_INTERVAL = 5;
    /** 箭矢速度 */
    public static final double ARROW_SPEED = 3.0;
    /** 箭矢存活刻数 */
    public static final int ARROW_LIFESPAN_TICKS = 30;
    /** 潜行时每 tick 消耗的能量 */
    public static final int BEAM_ENERGY_DRAIN_PER_TICK = 4000;
    /** 每点已消耗能量转化的伤害 */
    public static final double BEAM_DAMAGE_PER_ENERGY = 0.01;
    /** 单次潜行最大蓄力能量上限 */
    public static final int MAX_BEAM_CHARGE = 480000;
    /** 光束最大射程（格） */
    public static final double BEAM_MAX_RANGE = 80.0;
    /** 拉镜达到最大缩放所需 tick 数（1秒 = 20tick） */
    public static final int FOV_ZOOM_TICKS = 20;

    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_BEAM_CHARGE = "BeamCharge";

    /** 客户端潜行计时器（纯客户端维护，仅用于 FOV 缩放） */
    private static int clientSneakTicks = 0;

    // ==================== 构造 ====================

    public MachineGunItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    // ==================== 物品使用 ====================

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isCrouching()) {
            // 潜行模式：发射光束（瞬间，不进入持续使用状态）
            if (level.isClientSide) return InteractionResultHolder.consume(stack);

            long beamCharge = getBeamCharge(stack);
            if (beamCharge <= 0) {
                player.sendSystemMessage(Component.translatable("msg.chocomaker.machine_gun.no_charge"));
                return InteractionResultHolder.fail(stack);
            }

            fireBeam(level, player, beamCharge, false);
            setBeamCharge(stack, 0);
            playBeamSound(level, player);
            return InteractionResultHolder.consume(stack);
        } else {
            // 非潜行模式：按住右键连射
            if (!level.isClientSide && getEnergy(stack) <= 0) {
                player.sendSystemMessage(Component.translatable("msg.chocomaker.machine_gun.no_energy"));
                return InteractionResultHolder.fail(stack);
            }
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide) return;

        // 潜行时不应该进入 onUseTick，但做防御性检查
        if (player.isCrouching()) {
            player.stopUsingItem();
            return;
        }

        int usedTicks = getUseDuration(stack) - count;
        if (usedTicks > 0 && usedTicks % RAPID_FIRE_INTERVAL == 0) {
            long energy = getEnergy(stack);
            if (energy >= NORMAL_SHOT_COST) {
                fireProjectile(level, player, ARROW_SPEED);
                addEnergy(stack, -NORMAL_SHOT_COST);
                playFireSound(level, player);
            } else {
                player.stopUsingItem();
            }
        }
    }

    // ==================== Tick ====================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!(entity instanceof Player player)) return;

        if (level.isClientSide) {
            tickClientSneakTimer(player, isSelected);
        } else {
            tickServerSneakCharge(stack, player, isSelected);
        }
    }

    // ==================== 发射逻辑 ====================

    /**
     * 向玩家朝向发射一枚无重力、不可拾取、快速消失的箭矢
     */
    private void fireProjectile(Level level, Player player, double speed) {
        Vec3 look = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition();
        Vec3 spawnPos = eyePos.add(look.scale(0.5));

        Arrow arrow = new Arrow(level, player) {
            private int customLife = 0;
            @Override
            public void tick() {
                super.tick();
                customLife++;
                if (customLife >= ARROW_LIFESPAN_TICKS) {
                    this.discard();
                }
            }
        };
        arrow.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        arrow.shoot(look.x, look.y, look.z, (float) speed, 0.5f);
        arrow.setBaseDamage(speed * 2.0);
        arrow.setNoGravity(true);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.setCritArrow(false);
        level.addFreshEntity(arrow);
    }

    /**
     * 发射光束：对视线沿途所有生物造成伤害，伤害量由 beamCharge 决定。
     */
    public static void fireBeam(Level level, Player player, long beamCharge, boolean isFullCharge) {
        double damage = beamCharge * BEAM_DAMAGE_PER_ENERGY;
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(BEAM_MAX_RANGE));

        // 射线检测方块碰撞点
        net.minecraft.world.level.ClipContext clipCtx = new net.minecraft.world.level.ClipContext(
                start, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        );
        net.minecraft.world.phys.HitResult blockHit = level.clip(clipCtx);
        Vec3 hitPos = blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                ? blockHit.getLocation() : end;

        // 收集沿途所有生物
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(start, hitPos).inflate(1.0);
        java.util.function.Predicate<Entity> filter = e -> e instanceof LivingEntity
                && !e.is(player)
                && e.isAlive()
                && !e.isSpectator();

        for (Entity entity : level.getEntities(player, searchBox, filter)) {
            // 精确检测：射线是否命中该实体的包围盒
            net.minecraft.world.phys.AABB entityBox = entity.getBoundingBox().inflate(0.3);
            java.util.Optional<Vec3> clipResult = entityBox.clip(start, hitPos);
            if (clipResult.isPresent()) {
                LivingEntity living = (LivingEntity) entity;
                if (living instanceof Player) {
                    // 对玩家使用普通伤害（真实伤害对玩家无效）
                    living.hurt(level.damageSources().playerAttack(player), (float) damage);
                } else {
                    // 对非玩家生物使用真实伤害（削减血量上限）
                    applyTrueDamage(living, player, damage, isFullCharge);
                }
            }
        }

        // 粒子特效：沿光束路径生成
        spawnBeamParticles(level, start, hitPos);
    }

    /**
     * 沿光束路径生成粒子特效
     */
    private static void spawnBeamParticles(Level level, Vec3 start, Vec3 end) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        Vec3 dir = end.subtract(start);
        double length = dir.length();
        Vec3 step = dir.normalize().scale(0.5);
        int particleCount = (int) (length / 0.5);
        for (int i = 0; i <= particleCount; i++) {
            Vec3 pos = start.add(step.scale(i));
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z,
                    1, 0, 0, 0, 0
            );
        }
        // 命中点特效
        serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                end.x, end.y, end.z,
                1, 0, 0, 0, 0
        );
    }

    private void playFireSound(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.4f, 1.2f);
    }

    /**
     * 对目标施加真实伤害：削减血量上限。
     * 若伤害量超过目标血量上限，则直接视为玩家击杀。
     * 对玩家无效（调用前已过滤）。
     * <p>
     * 满蓄力时使用 {@link Entity#remove(Entity.RemovalReason)} 直接移除实体，
     * 穿透 LivingHurtEvent（Draconic Evolution / Avaritia 拦截层）和
     * LivingDeathEvent（不死图腾 / Corail Tombstone 拦截层）。
     */
    private static void applyTrueDamage(LivingEntity target, Player attacker, double damage, boolean isFullCharge) {
        double maxHealth = target.getMaxHealth();
        double newMaxHealth = Math.max(1.0, maxHealth - damage);

        // 削减血量上限
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);

        if (isFullCharge) {
            // 满蓄力：先走正常死亡流程以掉落战利品/经验，
            // 若被不死图腾等拦截则强制移除实体
            target.setHealth(0);
            target.die(target.level().damageSources().playerAttack(attacker));
            if (target.isAlive()) {
                target.remove(Entity.RemovalReason.KILLED);
            }
            return;
        }

        if (damage >= maxHealth) {
            // 真实伤害超过血量上限，直接击杀（视为玩家击杀）
            target.hurt(target.level().damageSources().playerAttack(attacker), Float.MAX_VALUE);
        } else {
            // 减少当前血量（绕过护甲）
            float newHealth = (float) Math.max(0, target.getHealth() - damage);
            if (newHealth <= 0) {
                target.hurt(target.level().damageSources().playerAttack(attacker), Float.MAX_VALUE);
            } else {
                target.setHealth(newHealth);
            }
        }
    }

    /**
     * 左键攻击实体时：若潜行且满蓄力，发射穿透光束（替代近战攻击）。
     * <p>
     * 客户端返回 true 取消挥臂动画；服务端发射光束并取消近战伤害。
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (!player.isCrouching()) return false;
        long beamCharge = getBeamCharge(stack);
        if (beamCharge < MAX_BEAM_CHARGE) return false;

        if (player.level().isClientSide) return true;

        // 满蓄力左键：发射穿透光束
        fireBeam(player.level(), player, beamCharge, true);
        setBeamCharge(stack, 0);
        playBeamSound(player.level(), player);
        return true;
    }

    public static void playBeamSound(Level level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    // ==================== 潜行蓄力 ====================

    /**
     * 服务端：潜行时消耗能量并累积蓄力
     */
    private void tickServerSneakCharge(ItemStack stack, Player player, boolean isSelected) {
        if (isSelected && player.isCrouching()) {
            long energy = getEnergy(stack);
            if (energy > 0) {
                long beamCharge = getBeamCharge(stack);
                if (beamCharge < MAX_BEAM_CHARGE) {
                    int drain = BEAM_ENERGY_DRAIN_PER_TICK;
                    if (beamCharge + drain > MAX_BEAM_CHARGE) {
                        drain = (int) (MAX_BEAM_CHARGE - beamCharge);
                    }
                    if (energy < drain) drain = (int) energy;
                    if (drain > 0) {
                        addEnergy(stack, -drain);
                        setBeamCharge(stack, beamCharge + drain);
                    }
                }
            }
        } else if (!player.isCrouching()) {
            // 停止潜行时重置蓄力（能量已消耗，不返还）
            setBeamCharge(stack, 0);
        }
    }

    // ==================== 潜行相关计算 ====================

    /**
     * 计算当前 FOV 缩放倍率（1.0 = 无缩放，越小越放大）
     * 缩放倍率提升：最大缩放到 0.15（原 0.3），望远效果更强
     */
    @OnlyIn(Dist.CLIENT)
    public static float getFovScale(int sneakTicks) {
        if (sneakTicks <= 0) return 1.0f;
        float ratio = Math.min((float) sneakTicks / FOV_ZOOM_TICKS, 1.0f);
        float eased = 1.0f - (1.0f - ratio) * (1.0f - ratio);
        return 1.0f - eased * 0.85f;
    }

    // ==================== 客户端潜行计时 ====================

    /**
     * 客户端更新潜行计时器（仅用于 FOV 缩放）。
     * 只需潜行 + 手持即可，无需按右键。
     */
    @OnlyIn(Dist.CLIENT)
    private void tickClientSneakTimer(Player player, boolean isSelected) {
        if (isSelected && player.isCrouching()) {
            clientSneakTicks++;
        } else {
            clientSneakTicks = 0;
        }
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

    public static void setEnergy(ItemStack stack, long amount) {
        stack.getOrCreateTag().putLong(TAG_ENERGY, Math.min(MAX_ENERGY, Math.max(0, amount)));
    }

    // ==================== 光束蓄力管理 ====================

    public static long getBeamCharge(ItemStack stack) {
        if (!stack.hasTag()) return 0;
        return stack.getOrCreateTag().getLong(TAG_BEAM_CHARGE);
    }

    public static void setBeamCharge(ItemStack stack, long charge) {
        stack.getOrCreateTag().putLong(TAG_BEAM_CHARGE, Math.max(0, charge));
    }

    // ==================== 显示 ====================

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long energy = getEnergy(stack);
        if (energy <= 0) return 0;
        // 以 MAX_ENERGY 能量为满条
        return Math.min(13, (int) (energy * 13.0 / MAX_ENERGY));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        long beamCharge = getBeamCharge(stack);
        if (beamCharge > 0) {
            // 蓄力中显示红色
            return 0xFF3333;
        }
        return 0x8B4513; // 巧克力棕色
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                java.util.List<Component> tooltip, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltip, isAdvanced);
        tooltip.add(Component.translatable("item.chocomaker.machine_gun.desc"));
        tooltip.add(Component.translatable("item.chocomaker.machine_gun.desc2"));
        tooltip.add(Component.translatable("item.chocomaker.machine_gun.desc3"));
        tooltip.add(Component.translatable("item.chocomaker.machine_gun.desc4"));
        tooltip.add(Component.translatable("item.chocomaker.machine_gun.desc5"));
        tooltip.add(Component.translatable("item.chocomaker.machine_gun.desc6"));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("item.chocomaker.machine_gun.energy", getEnergy(stack)));
        long beamCharge = getBeamCharge(stack);
        if (beamCharge > 0) {
            tooltip.add(Component.translatable("item.chocomaker.machine_gun.charge", beamCharge,
                    (int)(beamCharge * BEAM_DAMAGE_PER_ENERGY)));
        }
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

    // ==================== 事件处理器 ====================

    @Mod.EventBusSubscriber(modid = SCM.MODID)
    public static class MachineGunEventHandler {

        /**
         * 客户端 FOV 缩放 — 潜行时显示望远镜效果（无需右键）
         */
        @Mod.EventBusSubscriber(modid = SCM.MODID, value = Dist.CLIENT)
        public static class ClientFovHandler {
            @SubscribeEvent
            public static void onComputeFov(ComputeFovModifierEvent event) {
                Player player = event.getPlayer();
                ItemStack mainHand = player.getMainHandItem();
                if (!(mainHand.getItem() instanceof MachineGunItem)) return;

                if (clientSneakTicks > 0) {
                    float scale = getFovScale(clientSneakTicks);
                    event.setNewFovModifier(event.getNewFovModifier() * scale);
                }
            }
        }

        /**
         * 左键点击空气时：若潜行且满蓄力，发射穿透光束。
         * LeftClickEmpty 仅客户端触发，通过 MachineGunFirePacket 通知服务端。
         */
        @SubscribeEvent
        public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
            Player player = event.getEntity();
            if (!player.isCrouching()) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof MachineGunItem)) return;

            long beamCharge = getBeamCharge(stack);
            if (beamCharge < MAX_BEAM_CHARGE) return;

            // LeftClickEmpty 仅客户端触发，发送网络包到服务端执行
            if (player.level().isClientSide) {
                SCMNetwork.CHANNEL.sendToServer(new SCMNetwork.MachineGunFirePacket());
                return;
            }
        }

        /**
         * 拾取巧克力/电池时自动充能 — 手持可可机枪时拦截拾取事件，直接转化为能量
         */
        @SubscribeEvent
        public static void onItemPickup(EntityItemPickupEvent event) {
            Player player = event.getEntity();
            if (player.level().isClientSide()) return;
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof MachineGunItem)) return;

            ItemEntity itemEntity = event.getItem();
            ItemStack pickupStack = itemEntity.getItem();

            long currentEnergy = getEnergy(mainHand);
            if (currentEnergy >= MAX_ENERGY) {
                // 能量已满，不吸收，走正常拾取流程
                return;
            }

            int totalGain = SCMItems.getEnergyValueFE(pickupStack);
            if (totalGain <= 0) return;

            addEnergy(mainHand, totalGain);
            itemEntity.discard();
            event.setCanceled(true);

            long newEnergy = getEnergy(mainHand);
            if (newEnergy >= MAX_ENERGY) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("msg.chocomaker.machine_gun.energy_full", newEnergy));
            } else {
                serverPlayer.sendSystemMessage(
                        Component.translatable("msg.chocomaker.machine_gun.absorbed", newEnergy));
            }
        }
    }
}
