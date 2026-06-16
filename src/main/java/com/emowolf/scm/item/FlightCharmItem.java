package com.emowolf.scm.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FlightCharmItem extends Item implements ICurioItem {

    // 存储玩家的原始游戏模式
    private static final Map<UUID, GameType> playerOriginalGameMode = new HashMap<>();
    // 存储玩家冲刺状态，避免重复处理
    private static final Map<UUID, Boolean> playerSprintingState = new HashMap<>();
    // 存储玩家在旁观模式下的飞行速度累加值
    private static final Map<UUID, Float> playerFlightSpeed = new HashMap<>();

    // 默认飞行速度
    private static final float DEFAULT_FLIGHT_SPEED = 0.05f;
    // 每次累加的速度增量
    private static final float SPEED_INCREMENT = 0.005f;
    // 最大飞行速度
    private static final float MAX_FLIGHT_SPEED = 0.2f;

    public FlightCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.level().isClientSide()) return;

        UUID playerId = serverPlayer.getUUID();
        boolean isSprinting = serverPlayer.isSprinting();
        boolean wasSprinting = playerSprintingState.getOrDefault(playerId, false);
        boolean isFlying = serverPlayer.getAbilities().flying;

        playerSprintingState.put(playerId, isSprinting);

        // 飞行中开始冲刺 → 切换旁观模式
        if (isFlying && isSprinting && !wasSprinting) {
            playerOriginalGameMode.put(playerId, serverPlayer.gameMode.getGameModeForPlayer());
            serverPlayer.setGameMode(GameType.SPECTATOR);
            playerFlightSpeed.put(playerId, DEFAULT_FLIGHT_SPEED);
            serverPlayer.getAbilities().setFlyingSpeed(DEFAULT_FLIGHT_SPEED);
            serverPlayer.onUpdateAbilities();
        }
        // 停止冲刺且当前是旁观模式 → 恢复原始游戏模式
        else if (!isSprinting && serverPlayer.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            if (isPlayerInSafePosition(serverPlayer)) {
                GameType originalMode = playerOriginalGameMode.get(playerId);
                if (originalMode != null && originalMode != GameType.SPECTATOR) {
                    serverPlayer.setGameMode(originalMode);
                    playerOriginalGameMode.remove(playerId);
                    serverPlayer.getAbilities().flying = true;
                    Float flightSpeed = playerFlightSpeed.get(playerId);
                    if (flightSpeed != null) {
                        serverPlayer.getAbilities().setFlyingSpeed(flightSpeed);
                    }
                    serverPlayer.onUpdateAbilities();
                }
            }
        }
        // 旁观模式下飞行中 → 逐渐增加飞行速度
        else if (serverPlayer.gameMode.getGameModeForPlayer() == GameType.SPECTATOR &&
                 serverPlayer.getAbilities().flying) {
            Float currentSpeed = playerFlightSpeed.getOrDefault(playerId, DEFAULT_FLIGHT_SPEED);
            if (currentSpeed < MAX_FLIGHT_SPEED) {
                float newSpeed = Math.min(currentSpeed + SPEED_INCREMENT, MAX_FLIGHT_SPEED);
                playerFlightSpeed.put(playerId, newSpeed);
                serverPlayer.getAbilities().setFlyingSpeed(newSpeed);
                serverPlayer.onUpdateAbilities();
            }
        }
    }

    /**
     * 检查玩家是否处于安全位置（没有卡在方块中）
     */
    private static boolean isPlayerInSafePosition(ServerPlayer player) {
        Vec3 pos = player.position();
        BlockPos footPos = BlockPos.containing(pos.x, pos.y, pos.z);
        BlockPos bodyPos = BlockPos.containing(pos.x, pos.y + 1, pos.z);
        BlockPos headPos = BlockPos.containing(pos.x, pos.y + 1.8, pos.z);

        BlockState footBlock = player.level().getBlockState(footPos);
        BlockState bodyBlock = player.level().getBlockState(bodyPos);
        BlockState headBlock = player.level().getBlockState(headPos);

        boolean footPassable = footBlock.isAir() || footBlock.getCollisionShape(player.level(), footPos).isEmpty();
        boolean bodyPassable = bodyBlock.isAir() || bodyBlock.getCollisionShape(player.level(), bodyPos).isEmpty();
        boolean headPassable = headBlock.isAir() || headBlock.getCollisionShape(player.level(), headPos).isEmpty();

        return footPassable && bodyPassable && headPassable;
    }

    /**
     * 玩家登出时清理旁观冲刺相关的临时状态
     * 此事件处理器注册在 FlightCharmEventHandler 内部类中
     */
    static void cleanupPlayer(UUID playerId) {
        playerOriginalGameMode.remove(playerId);
        playerSprintingState.remove(playerId);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("item.chocomaker.flight_charm.desc"));
    }

    /**
     * 内部事件处理器，用于清理登出玩家的临时状态
     */
    @Mod.EventBusSubscriber(modid = com.emowolf.scm.SCM.MODID)
    public static class FlightCharmEventHandler {
        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            cleanupPlayer(event.getEntity().getUUID());
        }
    }
}
