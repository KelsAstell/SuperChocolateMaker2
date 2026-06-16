package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.block.SCMBlocks;
import com.emowolf.scm.effect.SCMEffects;
import com.emowolf.scm.item.SCMItems;
import com.emowolf.scm.item.TeleportCharmItem;
import com.emowolf.scm.network.SCMNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;

import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.world.InteractionResult;
import net.minecraftforge.network.NetworkDirection;

import java.util.*;
import java.util.function.BiConsumer;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class RitualHandler {

    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /* ========== 5x5 献祭台定义（复杂结构） ========== */

    // 统一祭坛底座：金块底座 + 红石块十字
    private static final Block ALTAR_BASE  = Blocks.GOLD_BLOCK;
    private static final Block ALTAR_CROSS = Blocks.REDSTONE_BLOCK;

    /* ========== 祭坛配方定义（修改此处自定义各仪式配方） ========== */

    // 0) 巧克力献祭 —— 催化剂：金块（堆叠巧克力提高成功率）
    private static final Block RECIPE_CHOCOLATE_CATALYST   = Blocks.GOLD_BLOCK;

    // 1) 无限流体仪式 —— 祭品：海洋之心，催化剂：钻石块
    private static final Item  RECIPE_FLUID_SACRIFICE     = Items.HEART_OF_THE_SEA;
    private static final Block RECIPE_FLUID_CATALYST       = Blocks.DIAMOND_BLOCK;

    // 2) 飞行仪式 —— 祭品：羽毛，催化剂：钻石块
    private static final Item  RECIPE_FLY_SACRIFICE        = Items.FEATHER;
    private static final Block RECIPE_FLY_CATALYST         = Blocks.DIAMOND_BLOCK;

    // 3) 反怪物Buff仪式 —— 祭品：腐肉，催化剂：铁块
    private static final Item  RECIPE_ANTI_MOB_SACRIFICE   = Items.ROTTEN_FLESH;
    private static final Block RECIPE_ANTI_MOB_CATALYST    = Blocks.IRON_BLOCK;

    // 4) 苹果吃法仪式 —— 祭品：蛋糕，催化剂：蛋糕
    private static final Item  RECIPE_APPLE_EAT_SACRIFICE  = Items.CAKE;
    private static final Block RECIPE_APPLE_EAT_CATALYST   = Blocks.CAKE;

    // 5) 无法破坏仪式 —— 祭品：任意物品，催化剂：铁块
    private static final Block RECIPE_UNBREAKABLE_CATALYST = Blocks.IRON_BLOCK;

    // 6) 永恒信标仪式 —— 祭品：信标，催化剂：绿宝石块
    private static final Item  RECIPE_BEACON_SACRIFICE     = Items.BEACON;
    private static final Block RECIPE_BEACON_CATALYST      = Blocks.EMERALD_BLOCK;

    // 7) 永远的搭档仪式 —— 祭品：下界之星，催化剂：绿宝石块
    private static final Item  RECIPE_PARTNER_SACRIFICE    = Items.NETHER_STAR;
    private static final Block RECIPE_PARTNER_CATALYST     = Blocks.EMERALD_BLOCK;

    // 8) 本手妙手抄手仪式 —— 祭品：呆毛，催化剂：绿宝石块
    // 延迟获取，避免在注册表就绪前调用 RegistryObject.get()
    private static Item ahoge() { return SCMItems.AHOGE.get(); }
    private static final Block RECIPE_BENSHOU_CATALYST     = Blocks.EMERALD_BLOCK;

    // 9) 维度护盾仪式 —— 祭品：呆毛，催化剂：下界合金块
    private static final Block RECIPE_DIMENSIONAL_SHIELD_CATALYST   = Blocks.NETHERITE_BLOCK;

    // 10) 飞行护符仪式 —— 祭品：呆毛，催化剂：钻石块
    private static final Block RECIPE_FLIGHT_CHARM_CATALYST   = Blocks.DIAMOND_BLOCK;

    // 11) 村民召唤仪式 —— 祭品：绿宝石，催化剂：草方块
    private static final Item  RECIPE_VILLAGER_SACRIFICE   = Items.EMERALD;
    private static final Block RECIPE_VILLAGER_CATALYST     = Blocks.GRASS_BLOCK;

    // 12) 闪电苦力怕召唤仪式 —— 祭品：火药，催化剂：避雷针
    private static final Item  RECIPE_CHARGED_CREEPER_SACRIFICE = Items.GUNPOWDER;
    private static final Block RECIPE_CHARGED_CREEPER_CATALYST  = Blocks.LIGHTNING_ROD;

    // 13) 电池盒仪式 —— 祭品：呆毛，催化剂：红石块
    private static final Block RECIPE_BATTERY_BOX_CATALYST = Blocks.REDSTONE_BLOCK;


    /* ========== 巧克力献祭系统 ========== */

    private static final int CHOCOLATE_RATE_PER = 1;
    private static final int MAX_CHOCOLATE = 128;

    private static final Map<BlockPos, AltarState> activeAltars = new HashMap<>();

    /** 仪式动画持续时长（tick），需与 RitualAnimationManager.DURATION 保持一致 */
    private static final int ANIMATION_DURATION = 120;

    /** 等待恢复重力 + 取消高亮的仪式产物，value = 剩余 tick */
    private static final Map<ItemEntity, Integer> pendingGravityRestore = new HashMap<>();

    static class AltarState {
        int offerings = 0;
    }

    /* ========== 事件入口 ========== */

    /** 右键黑曜石 + 巧克力：激活祭坛，将黑曜石替换为祭坛核心并生成悬浮显示 */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        ItemStack held = player.getItemInHand(event.getHand());

        if (!held.is(SCMItems.CHOCOLATE.get())) return;
        if (!level.getBlockState(pos).is(Blocks.OBSIDIAN)) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // 检查 5x5 祭坛结构（黑曜石中心 + 红石十字 + 金块底座）
        if (!checkAltarPattern5x5(level, pos)) return;

        // 激活祭坛：消耗 1 巧克力，替换黑曜石为祭坛核心
        held.shrink(1);
        serverLevel.setBlock(pos, SCMBlocks.ALTAR_CORE.get().defaultBlockState(), 3);

        // 激活特效
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        serverLevel.playSound(null, cx, cy, cz, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        for (int i = 0; i < 40; i++) {
            double angle = (i / 40.0) * Math.PI * 2;
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    cx + 1.5 * Math.cos(angle), cy + 0.5, cz + 1.5 * Math.sin(angle),
                    1, 0, 0.3, 0, 0.05);
        }

        player.sendSystemMessage(Component.translatable("ritual.chocomaker.altar_activated"));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** 方块破坏事件：祭坛核心被拆掉时移除悬浮显示 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.getState().is(SCMBlocks.ALTAR_CORE.get())) return;

        BlockPos pos = event.getPos();
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            cleanupAltarState(serverLevel, pos);
        }
    }

    /** 祭坛核心被破坏时的回调（由 AltarCoreBlock.onRemove 调用） */
    public static void onCoreBroken(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            cleanupAltarState(serverLevel, pos);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();

        if (level.isClientSide()) return;
        if (!(entity instanceof ItemEntity itemEntity)) return;
        if (!(itemEntity.getOwner() instanceof Player player)) return;

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        BlockPos itemPos = itemEntity.blockPosition();

        // 从物品位置搜索祭坛核心方块（物品可能落在 3x3 催化剂层任意位置，因此同时搜索水平方向）
        BlockPos foundCore = null;
        search:
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy >= -4; dy--) {
                    BlockPos checkPos = itemPos.offset(dx, dy, dz);
                    if (level.getBlockState(checkPos).is(SCMBlocks.ALTAR_CORE.get())) {
                        foundCore = checkPos;
                        break search;
                    }
                }
            }
        }
        if (foundCore == null) return;

        BlockPos basePos   = foundCore;          // 祭坛底座中心（祭坛核心方块位置）
        BlockPos catalystY = foundCore.above();  // 催化剂层（祭坛核心上方一格）

        ServerLevel serverLevel = (ServerLevel) level;
        boolean handled = false;
        try {

        // ── 0) 巧克力献祭（支持堆叠，自动计算进度）─────────────────────────────────────
        if (stack.is(SCMItems.CHOCOLATE.get()) && isValidAltarBase(level, basePos)) {
            handled = true;
            addChocolateOffering(serverLevel, basePos, player, stack.getCount());
            return;
        }

        // ── 1) 无限流体仪式 ──────────────────────────────────
        if (stack.is(RECIPE_FLUID_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_FLUID_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            RitualUnlockData data = RitualUnlockData.get(serverLevel);
            if (!data.isInfiniteFluidUnlocked()) {
                attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_FLUID_CATALYST,
                        () -> performUnlockRitual(serverLevel, itemEntity, itemPos, player,
                                data, RitualUnlockData::setInfiniteFluidUnlocked,
                                basePos));
            }
            return;
        }

        // ── 2) 飞行仪式 ──────────────────────────────────────
        if (stack.is(RECIPE_FLY_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_FLY_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            RitualUnlockData data = RitualUnlockData.get(serverLevel);
            if (!data.isFlightUnlocked()) {
                attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_FLY_CATALYST,
                        () -> performUnlockRitual(serverLevel, itemEntity, itemPos, player,
                                data, RitualUnlockData::setFlightUnlocked,
                                basePos));
            }
        }

        // ── 3) 反怪物Buff仪式 ────────────────────────────────
        if (stack.is(RECIPE_ANTI_MOB_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_ANTI_MOB_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            RitualUnlockData data = RitualUnlockData.get(serverLevel);
            if (!data.isAntiMobBuffUnlocked()) {
                attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_ANTI_MOB_CATALYST,
                        () -> performUnlockRitual(serverLevel, itemEntity, itemPos, player,
                                data, RitualUnlockData::setAntiMobBuffUnlocked,
                                basePos));
            }
        }

        // ── 4) 苹果吃法仪式 ──────────────────────────────────
        if (stack.is(RECIPE_APPLE_EAT_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_APPLE_EAT_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            RitualUnlockData data = RitualUnlockData.get(serverLevel);
            if (!data.isAppleEatUnlocked()) {
                attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_APPLE_EAT_CATALYST,
                        () -> performUnlockRitual(serverLevel, itemEntity, itemPos, player,
                                data, RitualUnlockData::setAppleEatUnlocked,
                                basePos));
            }
        }

        // ── 5) 无法破坏仪式 ──────────────────────────────────
        if (checkCatalystLayer(level, catalystY, RECIPE_UNBREAKABLE_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            boolean isAlreadyUnbreakable = stack.hasTag() && stack.getTag().getBoolean("Unbreakable");
            if (!isAlreadyUnbreakable) {
                performUnbreakableRitual(serverLevel, itemEntity, itemPos, player, stack, basePos);
                return;
            }
        }

        // ── 6) 永恒信标仪式 ──────────────────────────────────
        if (stack.is(RECIPE_BEACON_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_BEACON_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_BEACON_CATALYST,
                    () -> performEternalBeaconSuccess(serverLevel, itemEntity, itemPos, player, basePos));
            return;
        }

        // ── 7) 永远的搭档仪式 ────────────────────────────────
        if (stack.is(RECIPE_PARTNER_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_PARTNER_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            List<ServerPlayer> nearbyPlayers = getNearbyPlayers(serverLevel, basePos, 10.0);
            if (nearbyPlayers.size() != 2) {
                player.sendSystemMessage(Component.translatable("ritual.chocomaker.partner_need_two"));
                return;
            }
            ServerPlayer playerA = nearbyPlayers.get(0);
            ServerPlayer playerB = nearbyPlayers.get(1);
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_PARTNER_CATALYST,
                    () -> performPartnerRitual(serverLevel, itemEntity, itemPos, player, playerA, playerB, basePos));
            return;
        }

        // ── 8) 本手妙手抄手仪式 ──────────────────────────────
        if (stack.is(ahoge())
                && checkCatalystLayer(level, catalystY, RECIPE_BENSHOU_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_BENSHOU_CATALYST,
                    () -> performBenShouRitual(serverLevel, itemEntity, itemPos, player, basePos));
            return;
        }

        // ── 9) 维度护盾仪式 ──────────────────────────────────
        if (stack.is(ahoge())
                && checkCatalystLayer(level, catalystY, RECIPE_DIMENSIONAL_SHIELD_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_DIMENSIONAL_SHIELD_CATALYST,
                    () -> performDimensionalShieldRitual(serverLevel, itemEntity, itemPos, player, basePos));
            return;
        }

        // ── 10) 飞行护符仪式 ─────────────────────────────────
        if (stack.is(ahoge())
                && checkCatalystLayer(level, catalystY, RECIPE_FLIGHT_CHARM_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_FLIGHT_CHARM_CATALYST,
                    () -> performFlightCharmRitual(serverLevel, itemEntity, itemPos, player, basePos));
            return;
        }

        // ── 11) 村民召唤仪式 ─────────────────────────────────
        if (stack.is(RECIPE_VILLAGER_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_VILLAGER_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_VILLAGER_CATALYST,
                    () -> performVillagerSummonRitual(serverLevel, itemEntity, itemPos, player, basePos));
            return;
        }

        // ── 12) 闪电苦力怕召唤仪式 ───────────────────────────
        if (stack.is(RECIPE_CHARGED_CREEPER_SACRIFICE)
                && checkCatalystLayer(level, catalystY, RECIPE_CHARGED_CREEPER_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_CHARGED_CREEPER_CATALYST,
                    () -> performChargedCreeperRitual(serverLevel, itemEntity, itemPos, player, basePos));
            return;
        }

        // ── 13) 电池盒仪式 ──────────────────────────────────
        if (stack.is(ahoge())
                && checkCatalystLayer(level, catalystY, RECIPE_BATTERY_BOX_CATALYST)
                && checkAltarPattern5x5(level, basePos)) {
            handled = true;
            attemptRitual(serverLevel, itemEntity, itemPos, player, basePos, RECIPE_BATTERY_BOX_CATALYST,
                    () -> performBatteryBoxRitual(serverLevel, itemEntity, itemPos, player, basePos));
            return;
        }

        } finally {
            if (handled) {
                event.setCanceled(true); // 仅当仪式实际处理时才阻止物品实体加入世界
            }
        }
    }

    /* ========== 巧克力献祭管理 ========== */

    /** 检查催化剂层（y+1位置的3x3指定方块） */
    private static boolean checkCatalystLayer(Level level, BlockPos center, Block catalyst) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getBlockState(center.offset(dx, 0, dz)).is(catalyst)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 检查5x5祭坛底座 */
    private static boolean isValidAltarBase(Level level, BlockPos center) {
        return checkAltarPattern5x5(level, center);
    }

    private static void addChocolateOffering(ServerLevel level,
                                             BlockPos basePos, Player player, int count) {
        AltarState state = activeAltars.computeIfAbsent(basePos, k -> new AltarState());
        state.offerings = Math.min(state.offerings + count, MAX_CHOCOLATE);
        int totalOfferings = state.offerings;
        int rate = Math.min(totalOfferings * CHOCOLATE_RATE_PER, 100);

        // 音效 + 粒子
        double cx = basePos.getX() + 0.5, cy = basePos.getY() + 1.5, cz = basePos.getZ() + 0.5;
        level.playSound(null, cx, cy, cz, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8F, 1.0F + totalOfferings * 0.05F);
        int particleCount = Math.min(20 + count * 2, 60); // 根据数量增加粒子
        for (int i = 0; i < particleCount; i++) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    cx + (level.random.nextDouble() - 0.5) * 1.5,
                    cy + level.random.nextDouble() * 0.5,
                    cz + (level.random.nextDouble() - 0.5) * 1.5,
                    1, 0, 0, 0, 0.0);
        }

        player.sendSystemMessage(Component.translatable("ritual.chocomaker.chocolate_added", count, totalOfferings, rate));
    }

    private static void attemptRitual(ServerLevel level, ItemEntity itemEntity,
                                      BlockPos itemPos, Player player,
                                      BlockPos basePos, Block catalystBlock, Runnable onSuccess) {
        AltarState state = activeAltars.get(basePos);
        int offerings = (state != null) ? state.offerings : 0;
        int successRate = Math.min(offerings * CHOCOLATE_RATE_PER, 100);

        // 先播放献祭动画
        playRitualAnimation(level, itemPos, basePos, player);
        
        // 判定结果
        int roll = level.random.nextInt(100);
        
        if (roll < successRate) {
            onSuccess.run();
        } else {
            BlockPos catalystPos = basePos.above();
            handleRitualFailure(level, itemEntity, catalystPos, basePos,
                    player, offerings, successRate, catalystBlock);
        }
    }

    /** 播放献祭动画 */
    private static void playRitualAnimation(ServerLevel level, BlockPos itemPos, 
                                            BlockPos basePos, Player player) {
        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;
        
        // 音效：仪式开始
        level.playSound(null, cx, cy, cz, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
        
        // 阶段1: 催化剂层光环（3x3金块发光）
        for (int i = 0; i < 50; i++) {
            double angle = (i / 50.0) * Math.PI * 2;
            double radius = 1.2;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            level.sendParticles(ParticleTypes.GLOW, x, cy, z, 1, 0, 0, 0, 0.05);
        }
        
        // 阶段2: 从底座汇聚到祭品的粒子流
        for (int layer = 0; layer < 3; layer++) {
            double layerY = basePos.getY() + layer * 0.5;
            for (int i = 0; i < 30; i++) {
                double angle = (i / 30.0) * Math.PI * 2;
                double radius = 2.5 - layer * 0.5;
                double x = basePos.getX() + 0.5 + radius * Math.cos(angle);
                double z = basePos.getZ() + 0.5 + radius * Math.sin(angle);
                double targetX = cx, targetY = cy, targetZ = cz;
                
                // 向祭品汇聚的粒子
                level.sendParticles(ParticleTypes.ENCHANT, x, layerY, z, 1,
                        (targetX - x) * 0.1, (targetY - layerY) * 0.1, (targetZ - z) * 0.1, 0.0);
            }
        }
        
        // 阶段3: 祭品上方螺旋上升
        for (int i = 0; i < 40; i++) {
            double t = i / 40.0;
            double angle = t * Math.PI * 4;
            double radius = 0.5 + t * 1.5;
            double y = cy + t * 4.0;
            level.sendParticles(ParticleTypes.END_ROD,
                    cx + radius * Math.cos(angle), y, cz + radius * Math.sin(angle),
                    1, 0, 0.1, 0, 0.0);
        }
        
        // 阶段4: 红石块十字充能效果
        for (int i = 0; i < 4; i++) {
            int dx = 0, dz = 0;
            switch (i) {
                case 0: dx = 1; break;
                case 1: dx = -1; break;
                case 2: dz = 1; break;
                case 3: dz = -1; break;
            }
            BlockPos crossPos = basePos.offset(dx, 0, dz);
            double crossX = crossPos.getX() + 0.5;
            double crossZ = crossPos.getZ() + 0.5;
            
            for (int j = 0; j < 15; j++) {
                double t = j / 15.0;
                level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.0f, 0.0f), 1.0f),
                        crossX + (cx - crossX) * t,
                        basePos.getY() + t * 2.0,
                        crossZ + (cz - crossZ) * t,
                        1, 0, 0, 0, 0.0);
            }
        }
        
        // 提示消息
        player.sendSystemMessage(Component.translatable("ritual.chocomaker.ritual_started"));
    }

    /* ========== 仪式执行 ========== */

    private static void performUnlockRitual(ServerLevel level, ItemEntity itemEntity,
                                            BlockPos itemPos,
                                            Player player, RitualUnlockData data,
                                            BiConsumer<RitualUnlockData, Boolean> setter,
                                            BlockPos basePos) {
        itemEntity.discard();
        // 移除3x3催化剂层
        removeCatalystLayer(level, basePos.above());
        setter.accept(data, true);
        cleanupAltarState(level, basePos);

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;
        
        // 成功特效音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        
        // 成功粒子效果
        spawnAltarRitualParticles(level, itemPos);
        
        // 金色光环扩散
        for (int i = 0; i < 100; i++) {
            double angle = (i / 100.0) * Math.PI * 2;
            double radius = 3.0;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            level.sendParticles(ParticleTypes.GLOW, x, cy, z, 1, 0, 0.2, 0, 0.1);
        }
        sendRitualAnimation(player, basePos);
    }

    /** 移除3x3催化剂层 */
    private static void removeCatalystLayer(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(center.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void handleRitualFailure(ServerLevel level, ItemEntity itemEntity,
                                            BlockPos catalystPos, BlockPos basePos,
                                            Player player, int offerings, int successRate,
                                            Block catalystBlock) {
        itemEntity.discard();
        // 随机移除部分催化剂并替换为岩浆
        int removedCount = removeRandomCatalysts(level, catalystPos, catalystBlock);
        cleanupAltarState(level, basePos);

        double cx = basePos.getX() + 0.5, cy = basePos.getY() + 1.0, cz = basePos.getZ() + 0.5;

        // 失败特效音效
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.5F);
        level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.WITHER_HURT, SoundSource.BLOCKS, 1.5F, 0.6F);

        // 阶段1: 爆炸核心：火焰 + 浓烟
        for (int i = 0; i < 80; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double elev = (level.random.nextDouble() - 0.5) * Math.PI;
            double speed = 1.0 + level.random.nextDouble() * 3.0;
            double vx = Math.cos(angle) * Math.cos(elev) * speed;
            double vy = Math.sin(elev) * speed + 1.0;
            double vz = Math.sin(angle) * Math.cos(elev) * speed;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 1, vx * 0.1, vy * 0.1, vz * 0.1, 0.0);
            level.sendParticles(ParticleTypes.FLAME, cx, cy, cz, 1, vx * 0.1, vy * 0.1, vz * 0.1, 0.0);
        }

        // 阶段2: 冲击波扩展环
        for (int ring = 0; ring < 3; ring++) {
            double ringY = cy + ring * 0.5;
            double ringRadius = 1.0 + ring * 1.5;
            for (int i = 0; i < 40; i++) {
                double angle = 2 * Math.PI * i / 40;
                double x = cx + ringRadius * Math.cos(angle);
                double z = cz + ringRadius * Math.sin(angle);
                level.sendParticles(ParticleTypes.EXPLOSION, x, ringY, z, 1, 0, 0, 0, 0.0);
                level.sendParticles(ParticleTypes.LAVA, x, ringY + 0.1, z, 1, 0, 0.2, 0, 0.0);
            }
        }

        // 阶段3: 飞散碎片
        for (int i = 0; i < 30; i++) {
            double x = cx + (level.random.nextDouble() - 0.5) * 4;
            double y = cy + level.random.nextDouble() * 4;
            double z = cz + (level.random.nextDouble() - 0.5) * 4;
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 1,
                    (level.random.nextDouble() - 0.5) * 0.3,
                    level.random.nextDouble() * 0.3,
                    (level.random.nextDouble() - 0.5) * 0.3, 0.0);
        }

        // 阶段4: 岩浆爆发效果（从催化剂位置）
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = catalystPos.offset(dx, 0, dz);
                if (level.getBlockState(pos).is(Blocks.LAVA)) {
                    double lavaX = pos.getX() + 0.5;
                    double lavaZ = pos.getZ() + 0.5;
                    for (int i = 0; i < 20; i++) {
                        level.sendParticles(ParticleTypes.LAVA, lavaX, pos.getY() + 0.5, lavaZ, 1,
                                (level.random.nextDouble() - 0.5) * 0.5,
                                level.random.nextDouble() * 0.8,
                                (level.random.nextDouble() - 0.5) * 0.5, 0.0);
                    }
                }
            }
        }

        // 散落巧克力物品（带物理弹射）
        int scatterCount = Math.max(1, offerings / 2);
        for (int i = 0; i < scatterCount; i++) {
            ItemEntity chocolate = new ItemEntity(level, cx, cy + 1, cz,
                    new ItemStack(SCMItems.CHOCOLATE.get()));
            chocolate.setDeltaMovement(
                    (level.random.nextDouble() - 0.5) * 1.0,
                    0.5 + level.random.nextDouble() * 1.0,
                    (level.random.nextDouble() - 0.5) * 1.0);
            chocolate.setDefaultPickUpDelay();
            level.addFreshEntity(chocolate);
        }

        // 对周围玩家施加虚数污染效果（10秒）
        double pollutionRadius = 10.0;
        for (ServerPlayer sp : level.getPlayers(p ->
                p.distanceToSqr(cx, cy, cz) <= pollutionRadius * pollutionRadius)) {
            if (!sp.hasEffect(SCMEffects.IMAGINARY_RESISTANCE.get())) {
                sp.addEffect(new MobEffectInstance(SCMEffects.IMAGINARY_POLLUTION.get(), 200, 0));
            }
        }

        Component message = Component.translatable("ritual.chocomaker.ritual_failed",
                player.getName().getString(), successRate, removedCount);
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            sp.sendSystemMessage(message);
        }
    }

    /** 随机移除部分催化剂并替换为岩浆，返回移除数量 */
    private static int removeRandomCatalysts(ServerLevel level, BlockPos center, Block catalystBlock) {
        // 收集所有催化剂位置
        List<BlockPos> catalystPositions = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (level.getBlockState(pos).is(catalystBlock)) {
                    catalystPositions.add(pos);
                }
            }
        }
        
        // 随机打乱
        Collections.shuffle(catalystPositions, new java.util.Random(level.random.nextLong()));
        
        // 随机移除 2-5 个催化剂
        int removeCount = 2 + level.random.nextInt(4); // 2-5
        removeCount = Math.min(removeCount, catalystPositions.size());
        
        for (int i = 0; i < removeCount; i++) {
            level.setBlock(catalystPositions.get(i), Blocks.LAVA.defaultBlockState(), 3);
        }
        
        return removeCount;
    }

    private static void cleanupAltarState(ServerLevel level, BlockPos basePos) {
        activeAltars.remove(basePos);
    }

    /* ========== 无法破坏仪式 ========== */

    /**
     * 对投入的物品附加无法破坏效果
     * 催化剂：绿宝石块（3x3）
     */
    private static void performUnbreakableRitual(ServerLevel level, ItemEntity itemEntity,
                                                  BlockPos itemPos, Player player, ItemStack stack, BlockPos basePos) {
        // 先播放仪式动画
        playRitualAnimation(level, itemPos, basePos, player);
        
        // 处理无法破坏仪式
        // 为物品附加无法破坏标签
        itemEntity.discard();
        
        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());
        
        // 返还附加了无法破坏效果的物品（悬浮于祭坛上方4格）
        ItemStack enchantedStack = stack.copy();
        enchantedStack.getOrCreateTag().putBoolean("Unbreakable", true);
        spawnRitualResultItem(level, basePos, enchantedStack, 60);
        
        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;
        
        // 成功特效音效
        level.playSound(null, cx, cy, cz, SoundEvents.ITEM_BREAK, SoundSource.BLOCKS, 0.5F, 2.0F);
        level.playSound(null, cx, cy, cz, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.5F);
        level.playSound(null, cx, cy, cz, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.2F);
        
        // 成功粒子效果
        for (int i = 0; i < 60; i++) {
            double angle = (i / 60.0) * Math.PI * 2;
            double radius = 1.5;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            level.sendParticles(ParticleTypes.ENCHANT, x, cy, z, 1, 0, 0.5, 0, 0.1);
            level.sendParticles(ParticleTypes.GLOW, x, cy + 0.5, z, 1, 0, 0, 0, 0.05);
        }
        
        // 向上螺旋粒子
        for (int i = 0; i < 40; i++) {
            double t = i / 40.0;
            double angle = t * Math.PI * 4;
            double radius = 0.3 + t * 1.0;
            double y = cy + t * 3.0;
            level.sendParticles(ParticleTypes.END_ROD,
                    cx + radius * Math.cos(angle), y, cz + radius * Math.sin(angle),
                    1, 0, 0.1, 0, 0.0);
        }
        
        // 提示消息
        Component itemName = enchantedStack.getHoverName();
        player.sendSystemMessage(Component.translatable("ritual.chocomaker.unbreakable_success", itemName.getString()));
        sendRitualAnimation(player, basePos);
    }

    /* ========== 永恒信标仪式 ========== */

    /**
     * 永恒信标仪式成功回调：消耗信标 + 9个绿宝石块催化剂，生成永恒信标
     */
    private static void performEternalBeaconSuccess(ServerLevel level, ItemEntity itemEntity,
                                                    BlockPos itemPos, Player player, BlockPos basePos) {
        
        // 消耗祭品
        itemEntity.discard();
        
        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());
        
        // 清理祭坛状态
        cleanupAltarState(level, basePos);
        
        // 生成永恒信标物品（悬浮于祭坛上方4格）
        ItemStack beaconStack = new ItemStack(SCMBlocks.ETERNAL_BEACON_ITEM.get());
        spawnRitualResultItem(level, basePos, beaconStack, 60);
        
        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;
        
        // 成功特效音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 1.5F);
        
        // 成功粒子效果
        spawnAltarRitualParticles(level, itemPos);
        
        // 绿色光环扩散
        for (int i = 0; i < 100; i++) {
            double angle = (i / 100.0) * Math.PI * 2;
            double radius = 3.0;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            level.sendParticles(ParticleTypes.GLOW, x, cy, z, 1, 0, 0.2, 0, 0.1);
        }
        
        // 信标光束效果
        for (int i = 0; i < 60; i++) {
            level.sendParticles(ParticleTypes.END_ROD, cx, cy + i * 0.3, cz, 1, 0, 0, 0, 0.0);
        }
        sendRitualAnimation(player, basePos);
    }

    /* ========== 永远的搭档仪式 ========== */

    /**
     * 永远的搭档仪式成功回调：消耗下界之星 + 9个绿宝石块催化剂，
     * 生成两个传送护符，分别绑定两名玩家的信息
     */
    private static void performPartnerRitual(ServerLevel level, ItemEntity itemEntity,
                                             BlockPos itemPos, Player initiator,
                                             ServerPlayer playerA, ServerPlayer playerB, BlockPos basePos) {

        // 消耗祭品
        itemEntity.discard();

        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());

        // 清理祭坛状态
        cleanupAltarState(level, basePos);

        // 创建护符A：绑定玩家B的信息（玩家A使用可传送到B）
        ItemStack charmA = new ItemStack(SCMItems.TELEPORT_CHARM.get());
        charmA.getOrCreateTag().putUUID(TeleportCharmItem.TAG_TARGET_UUID, playerB.getUUID());
        charmA.getOrCreateTag().putString(TeleportCharmItem.TAG_TARGET_NAME, playerB.getGameProfile().getName());

        // 创建护符B：绑定玩家A的信息（玩家B使用可传送到A）
        ItemStack charmB = new ItemStack(SCMItems.TELEPORT_CHARM.get());
        charmB.getOrCreateTag().putUUID(TeleportCharmItem.TAG_TARGET_UUID, playerA.getUUID());
        charmB.getOrCreateTag().putString(TeleportCharmItem.TAG_TARGET_NAME, playerA.getGameProfile().getName());

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;

        // 生成护符A（悬浮于祭坛上方4格，末影龙死亡光芒特效）
        spawnRitualResultItem(level, basePos, charmA, 60);

        // 生成护符B（悬浮于护符A旁稍偏移）
        double bx = basePos.getX() + 0.8;
        double by = basePos.getY() + 4.5;
        double bz = basePos.getZ() + 0.5;
        ItemEntity resultB = new ItemEntity(level, bx, by, bz, charmB);
        resultB.setNoGravity(true);
        resultB.setGlowingTag(true);           // 高亮显示
        resultB.setPickUpDelay(60);
        level.addFreshEntity(resultB);

        // 调度：动画结束后恢复重力并取消高亮
        pendingGravityRestore.put(resultB, ANIMATION_DURATION);

        // 成功特效音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 成功粒子效果
        spawnAltarRitualParticles(level, itemPos);

        // 爱心粒子效果（环绕两个玩家）
        for (ServerPlayer sp : new ServerPlayer[]{playerA, playerB}) {
            double px = sp.getX(), py = sp.getY() + 1.0, pz = sp.getZ();
            for (int i = 0; i < 30; i++) {
                double angle = (i / 30.0) * Math.PI * 2;
                double radius = 1.0;
                level.sendParticles(ParticleTypes.HEART,
                        px + radius * Math.cos(angle), py + i * 0.1, pz + radius * Math.sin(angle),
                        1, 0, 0.05, 0, 0.0);
            }
        }
        sendRitualAnimation(initiator, basePos);
    }

    /* ========== 本手妙手抄手仪式 ========== */

    /**
     * 本手妙手抄手仪式成功回调：消耗呆毛 + 9个绿宝石块催化剂，生成本手妙手抄手饰品
     */
    private static void performBenShouRitual(ServerLevel level, ItemEntity itemEntity,
                                             BlockPos itemPos, Player player, BlockPos basePos) {

        // 消耗祭品
        itemEntity.discard();

        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());

        // 清理祭坛状态
        cleanupAltarState(level, basePos);

        // 生成本手妙手抄手（悬浮于祭坛上方4格）
        ItemStack benShouStack = new ItemStack(SCMItems.BEN_SHOU_MIAO_SHOU_CHAO_SHOU.get());
        spawnRitualResultItem(level, basePos, benShouStack, 60);

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;

        // 成功音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 粒子效果
        spawnAltarRitualParticles(level, itemPos);
        sendRitualAnimation(player, basePos);
    }

    /* ========== 维度护盾仪式 ========== */

    /**
     * 维度护盾仪式成功回调：消耗呆毛 + 9个下界合金块催化剂，生成维度护盾饰品
     */
    private static void performDimensionalShieldRitual(ServerLevel level, ItemEntity itemEntity,
                                                        BlockPos itemPos, Player player, BlockPos basePos) {

        // 消耗祭品
        itemEntity.discard();

        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());

        // 清理祭坛状态
        cleanupAltarState(level, basePos);

        // 生成维度护盾（悬浮于祭坛上方4格）
        ItemStack shieldStack = new ItemStack(SCMItems.DIMENSIONAL_SHIELD.get());
        spawnRitualResultItem(level, basePos, shieldStack, 60);

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;

        // 成功音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 粒子效果
        spawnAltarRitualParticles(level, itemPos);
        sendRitualAnimation(player, basePos);
    }

    /* ========== 飞行护符仪式 ========== */

    /**
     * 飞行护符仪式成功回调：消耗呆毛 + 9个钻石块催化剂，生成飞行护符饰品
     */
    private static void performFlightCharmRitual(ServerLevel level, ItemEntity itemEntity,
                                                  BlockPos itemPos, Player player, BlockPos basePos) {

        // 消耗祭品
        itemEntity.discard();

        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());

        // 清理祭坛状态
        cleanupAltarState(level, basePos);

        // 生成飞行护符（悬浮于祭坛上方4格）
        ItemStack charmStack = new ItemStack(SCMItems.FLIGHT_CHARM.get());
        spawnRitualResultItem(level, basePos, charmStack, 60);

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;

        // 成功音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 粒子效果
        spawnAltarRitualParticles(level, itemPos);
        sendRitualAnimation(player, basePos);
    }

    /* ========== 村民召唤仪式 ========== */

    /**
     * 村民召唤仪式成功回调：消耗绿宝石 + 9个草方块催化剂，在祭坛位置生成一只村民
     */
    private static void performVillagerSummonRitual(ServerLevel level, ItemEntity itemEntity,
                                                     BlockPos itemPos, Player player, BlockPos basePos) {

        // 消耗祭品
        itemEntity.discard();

        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());

        // 清理祭坛状态
        cleanupAltarState(level, basePos);

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;

        // 生成村民
        net.minecraft.world.entity.npc.Villager villager = 
                new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER, level);
        villager.setPos(cx, cy, cz);
        level.addFreshEntity(villager);

        // 成功音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.VILLAGER_CELEBRATE, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 粒子效果
        spawnAltarRitualParticles(level, itemPos);

        // 绿色光环扩散（代表村民）
        for (int i = 0; i < 80; i++) {
            double angle = (i / 80.0) * Math.PI * 2;
            double radius = 3.0;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, cy, z, 1, 0, 0.2, 0, 0.1);
        }
        sendRitualAnimation(player, basePos);
    }

    /* ========== 闪电苦力怕召唤仪式 ========== */

    /**
     * 闪电苦力怕召唤仪式成功回调：消耗火药 + 9个避雷针催化剂，在祭坛位置生成一只闪电苦力怕
     */
    private static void performChargedCreeperRitual(ServerLevel level, ItemEntity itemEntity,
                                                     BlockPos itemPos, Player player, BlockPos basePos) {

        // 消耗祭品
        itemEntity.discard();

        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());

        // 清理祭坛状态
        cleanupAltarState(level, basePos);

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;

        // 生成苦力怕
        net.minecraft.world.entity.monster.Creeper creeper = 
                new net.minecraft.world.entity.monster.Creeper(EntityType.CREEPER, level);
        creeper.setPos(cx, cy, cz);
        level.addFreshEntity(creeper);

        // 闪电劈中苦力怕使其变为闪电苦力怕
        net.minecraft.world.entity.LightningBolt lightning = 
                new net.minecraft.world.entity.LightningBolt(EntityType.LIGHTNING_BOLT, level);
        lightning.setPos(cx, cy, cz);
        level.addFreshEntity(lightning);

        // 成功音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.5F, 0.5F);

        // 粒子效果
        spawnAltarRitualParticles(level, itemPos);

        // 闪电粒子扩散
        for (int i = 0; i < 60; i++) {
            double angle = (i / 60.0) * Math.PI * 2;
            double radius = 2.5;
            double x = cx + radius * Math.cos(angle);
            double z = cz + radius * Math.sin(angle);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, cy + 0.5, z, 1, 0, 0, 0, 0.0);
        }
        sendRitualAnimation(player, basePos);
    }

    /* ========== 电池盒仪式 ========== */

    /**
     * 电池盒仪式成功回调：消耗呆毛 + 9个红石块催化剂，生成电池盒
     */
    private static void performBatteryBoxRitual(ServerLevel level, ItemEntity itemEntity,
                                                 BlockPos itemPos, Player player, BlockPos basePos) {

        // 消耗祭品
        itemEntity.discard();

        // 移除催化剂层
        removeCatalystLayer(level, basePos.above());

        // 清理祭坛状态
        cleanupAltarState(level, basePos);

        // 生成电池盒（悬浮于祭坛上方4格）
        ItemStack batteryBoxStack = new ItemStack(SCMItems.BATTERY_BOX.get());
        spawnRitualResultItem(level, basePos, batteryBoxStack, 60);

        double cx = itemPos.getX() + 0.5, cy = itemPos.getY() + 0.5, cz = itemPos.getZ() + 0.5;

        // 成功音效
        level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8F, 1.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);

        // 粒子效果
        spawnAltarRitualParticles(level, itemPos);
        sendRitualAnimation(player, basePos);
    }

    /** 获取祭坛附近指定半径内的玩家列表 */
    private static List<ServerPlayer> getNearbyPlayers(ServerLevel level, BlockPos center, double radius) {
        List<ServerPlayer> result = new ArrayList<>();
        double cx = center.getX() + 0.5, cy = center.getY() + 0.5, cz = center.getZ() + 0.5;
        for (ServerPlayer sp : level.getServer().getPlayerList().getPlayers()) {
            if (sp.distanceToSqr(cx, cy, cz) <= radius * radius) {
                result.add(sp);
            }
        }
        return result;
    }

    /* ========== 5x5 底座检测 ========== */

    /**
     * 检查5x5祭坛图案：
     * - 金块底座（5x5全填充）
     * - 红石块十字（中心十字5格）
     * - 中心：黑曜石
     * 
     * 结构示例：
     * 🟡🟡🔴🟡🟡
     * 🟡🟡🔴🟡🟡
     * 🔴🔴⬛🔴🔴
     * 🟡🟡🔴🟡🟡
     * 🟡🟡🔴🟡🟡
     */
    private static boolean checkAltarPattern5x5(Level level, BlockPos center) {
        // 中心可以是黑曜石（未激活）或祭坛核心（已激活），此处仅检查周围图案
        
        // 检查5x5范围
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                
                // 红石块十字位置（中心 + 上下左右各2格）
                boolean isCrossPosition = (dx == 0 && Math.abs(dz) <= 2) || 
                                          (dz == 0 && Math.abs(dx) <= 2);
                
                if (isCrossPosition) {
                    // 中心已检查过，十字位置需要是红石块
                    if (dx != 0 || dz != 0) {
                        if (!level.getBlockState(pos).is(ALTAR_CROSS)) return false;
                    }
                } else {
                    // 其他位置需要是金块
                    if (!level.getBlockState(pos).is(ALTAR_BASE)) return false;
                }
            }
        }
        return true;
    }

    /* ========== 粒子效果 ========== */

    private static void spawnAltarRitualParticles(ServerLevel level, BlockPos center) {
        double cx = center.getX() + 0.5, cy = center.getY(), cz = center.getZ() + 0.5;

        for (int i = 0; i < 60; i++) {
            double t = i / 60.0;
            double angle = t * Math.PI * 6;
            double radius = 1.2 * (1.0 - t * 0.3);
            double y = cy + t * 6.0;
            level.sendParticles(ParticleTypes.END_ROD, cx + radius * Math.cos(angle), y, cz + radius * Math.sin(angle), 1, 0, 0, 0, 0.0);
            level.sendParticles(ParticleTypes.ENCHANT, cx + radius * Math.cos(angle + Math.PI), y, cz + radius * Math.sin(angle + Math.PI), 1, 0, 0, 0, 0.0);
        }
        for (int ring = 0; ring < 4; ring++) {
            double ringY = cy + 0.05 + ring * 0.25;
            double ringR = 0.5 + ring * 0.6;
            int count = 24 + ring * 8;
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double x = cx + ringR * Math.cos(angle);
                double z = cz + ringR * Math.sin(angle);
                level.sendParticles(ParticleTypes.ENCHANT, x, ringY, z, 1, 0, 0, 0, 0.0);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, ringY + 0.05, z, 1, 0, 0, 0, 0.0);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                double sx = center.getX() + dx + 0.5, sz = center.getZ() + dz + 0.5;
                for (int i = 0; i < 8; i++) {
                    double t = i / 8.0;
                    level.sendParticles(ParticleTypes.END_ROD, sx + (cx - sx) * t, cy + t * 4.0, sz + (cz - sz) * t, 1, 0, 0, 0, 0.0);
                }
            }
        }
        for (int i = 0; i < 40; i++) {
            level.sendParticles(ParticleTypes.END_ROD, cx, cy + i * 0.2, cz, 1, 0, 0, 0, 0.0);
        }
    }

    /* ========== 产物重力恢复调度 ========== */

    /**
     * 服务端 tick：倒计时恢复产物重力和取消高亮
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingGravityRestore.isEmpty()) return;

        Iterator<Map.Entry<ItemEntity, Integer>> it = pendingGravityRestore.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ItemEntity, Integer> entry = it.next();
            ItemEntity item = entry.getKey();
            int remaining = entry.getValue() - 1;

            if (remaining <= 0 || !item.isAlive()) {
                if (item.isAlive()) {
                    item.setNoGravity(false);
                    // 高亮保留不取消，让产物持续发光
                }
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    /* ========== 动画网络包 ========== */

    private static void sendRitualAnimation(Player player, BlockPos basePos) {
        if (player instanceof ServerPlayer sp) {
            SCMNetwork.CHANNEL.sendTo(
                    new SCMNetwork.RitualAnimationPacket(basePos),
                    sp.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT);
        }
    }

    /* ========== 仪式产物生成 ========== */

    /**
     * 生成仪式产物：物品悬浮于祭坛上方4格，播放末影龙死亡光芒特效（缩小版），
     * 动画期间不可拾取。
     *
     * @param level            服务器世界
     * @param basePos          祭坛核心位置
     * @param resultStack      产物物品堆
     * @param pickupDelayTicks 拾取延迟（tick），动画期间建议 60~100
     */
    private static void spawnRitualResultItem(ServerLevel level, BlockPos basePos,
                                              ItemStack resultStack, int pickupDelayTicks) {
        double cx = basePos.getX() + 0.5;
        double cy = basePos.getY() + 4.5; // 祭坛核心上方4格
        double cz = basePos.getZ() + 0.5;

        ItemEntity resultItem = new ItemEntity(level, cx, cy, cz, resultStack);
        resultItem.setNoGravity(true);            // 悬浮
        resultItem.setGlowingTag(true);           // 高亮显示
        resultItem.setPickUpDelay(pickupDelayTicks); // 动画期间不可拾取
        level.addFreshEntity(resultItem);

        // 调度：动画结束后恢复重力并取消高亮
        pendingGravityRestore.put(resultItem, ANIMATION_DURATION);

        // ── 末影龙死亡光芒（缩小版）──
        // 音效
        level.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_DEATH,
                SoundSource.BLOCKS, 0.5F, 1.0F);

        // 光芒柱：从祭坛核心向上穿透至物品位置
        for (int i = 0; i < 35; i++) {
            double y = basePos.getY() + 0.5 + i * 0.12;
            level.sendParticles(ParticleTypes.END_ROD, cx, y, cz, 1, 0, 0, 0, 0.0);
            if (i % 2 == 0) {
                level.sendParticles(ParticleTypes.GLOW, cx, y + 0.05, cz, 1, 0, 0, 0, 0.0);
            }
        }

        // 扩散光环（环形光芒向外扩散）
        for (int ring = 0; ring < 4; ring++) {
            double ringY = cy - 0.5 + ring * 0.6;
            double ringR = 0.5 + ring * 0.5;
            int count = 16 + ring * 4;
            for (int i = 0; i < count; i++) {
                double angle = 2 * Math.PI * i / count;
                double x = cx + ringR * Math.cos(angle);
                double z = cz + ringR * Math.sin(angle);
                level.sendParticles(ParticleTypes.GLOW, x, ringY, z, 1, 0, 0, 0, 0.0);
            }
        }

        // 顶部爆裂粒子
        for (int i = 0; i < 20; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double elev = (level.random.nextDouble() - 0.5) * Math.PI;
            double speed = 0.5 + level.random.nextDouble() * 1.0;
            level.sendParticles(ParticleTypes.END_ROD,
                    cx + Math.cos(angle) * Math.cos(elev) * 0.2,
                    cy,
                    cz + Math.sin(angle) * Math.cos(elev) * 0.2,
                    1,
                    Math.cos(angle) * Math.cos(elev) * speed * 0.1,
                    Math.sin(elev) * speed * 0.1,
                    Math.sin(angle) * Math.cos(elev) * speed * 0.1,
                    0.0);
        }
    }
}
