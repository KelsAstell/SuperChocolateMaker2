package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.item.DimensionalShieldItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class DimensionalShieldHandler {

    private static final String CURIOS_MODID = "curios";

    /** 即死判定阈值：伤害超过玩家最大生命值的此倍数视为模组秒杀 */
    private static final double INSTANT_KILL_MULTIPLIER = 100.0;

    // ========== 伤害吸收 + 即死拦截 ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ModList.get().isLoaded(CURIOS_MODID)) return;

        ItemStack shield = DimensionalShieldItem.findEquippedShield(player);
        if (shield == null) return;

        double shieldValue = DimensionalShieldItem.getShieldValue(shield);
        if (shieldValue <= 0) return;

        float damage = event.getAmount();

        // 增加负载（连续受击叠加）
        DimensionalShieldItem.addLoad(shield, player.level().getGameTime());
        int load = DimensionalShieldItem.getLoad(shield);

        // 判定即死攻击：极高伤害（模组秒杀）或原版即死类型
        if (isInstantKillAttempt(event, player)) {
            double halved = shieldValue / 2.0;
            DimensionalShieldItem.setShieldValue(shield, halved);
            event.setCanceled(true);
            player.sendSystemMessage(Component.translatable("msg.chocomaker.dimensional_shield.instant_kill_blocked",
                    DimensionalShieldItem.formatCompact(shieldValue - halved)));
            return;
        }

        // 正常伤害吸收：总耗损 = 伤害值 + 负载额外耗损
        double extraDrain = DimensionalShieldItem.calcExtraDrain(damage, load);
        double totalDrain = damage + extraDrain;
        if (shieldValue >= totalDrain) {
            DimensionalShieldItem.setShieldValue(shield, shieldValue - totalDrain);
            event.setCanceled(true);
        } else {
            float remaining = damage - (float) shieldValue;
            DimensionalShieldItem.setShieldValue(shield, 0);
            event.setAmount(remaining);
        }
    }

    /**
     * 判定是否为即死攻击：极高伤害值（模组常用 Float.MAX_VALUE 等手段）或原版即死类型。
     */
    private static boolean isInstantKillAttempt(LivingHurtEvent event, ServerPlayer player) {
        float damage = event.getAmount();

        // 模组秒杀：设置极高伤害值（如 Float.MAX_VALUE）
        if (damage >= player.getMaxHealth() * INSTANT_KILL_MULTIPLIER) {
            return true;
        }

        // 原版即死类型：/kill 命令、虚空
        String msgId = event.getSource().getMsgId();
        if ("genericKill".equals(msgId) || "outOfWorld".equals(msgId)) {
            return true;
        }

        return false;
    }

    // ========== setHealth(0) / 直接致死拦截 ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ModList.get().isLoaded(CURIOS_MODID)) return;

        ItemStack shield = DimensionalShieldItem.findEquippedShield(player);
        if (shield == null) return;

        double shieldValue = DimensionalShieldItem.getShieldValue(shield);
        if (shieldValue <= 0) return;

        // 护盾值 > 0 却触发死亡 → setHealth(0) 等绕过伤害系统的致死手段
        double halved = shieldValue / 2.0;
        DimensionalShieldItem.setShieldValue(shield, halved);
        event.setCanceled(true);
        player.setHealth(1.0f);
        player.sendSystemMessage(Component.translatable("msg.chocomaker.dimensional_shield.instant_kill_blocked",
                DimensionalShieldItem.formatCompact(shieldValue - halved)));
    }

    // ========== 巧克力电池拾取 ==========

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!ModList.get().isLoaded(CURIOS_MODID)) return;

        ItemEntity itemEntity = event.getItem();
        ItemStack pickupStack = itemEntity.getItem();

        if (!pickupStack.is(SCMItems.CHOCOLATE_BATTERY.get())) return;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(SCMItems.DIMENSIONAL_SHIELD.get())) return;

        int count = pickupStack.getCount();

        ItemStack equippedShield = DimensionalShieldItem.findEquippedShield(serverPlayer);
        if (equippedShield == null) {
            DimensionalShieldItem.increaseRegenRate(mainHand, count);
        } else {
            DimensionalShieldItem.increaseRegenRate(equippedShield, count);
        }

        itemEntity.discard();
        event.setCanceled(true);

        double newRate = equippedShield != null
                ? DimensionalShieldItem.getRegenRate(equippedShield)
                : DimensionalShieldItem.getRegenRate(mainHand);
        serverPlayer.sendSystemMessage(Component.translatable("msg.chocomaker.dimensional_shield.battery_consumed",
                String.format("%.1f", newRate)));
    }
}
