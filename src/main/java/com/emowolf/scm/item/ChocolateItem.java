package com.emowolf.scm.item;

import com.emowolf.scm.Configs;
import com.emowolf.scm.SCM;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class ChocolateItem extends Item {
    private static final java.util.Map<UUID, Double> playerChocolateHealthBonus = new java.util.HashMap<>();

    public ChocolateItem() {
        super(new Properties()
                .food(new FoodProperties.Builder()
                        .nutrition(2)
                        .saturationMod(0.2f)
                        .alwaysEat()
                        .fast()
                        .build())
                .stacksTo(64));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            UUID playerId = player.getUUID();
            if (player instanceof ServerPlayer serverPlayer) {
                double currentMaxHealth = serverPlayer.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
                serverPlayer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(currentMaxHealth + 2.0);
                playerChocolateHealthBonus.put(playerId, playerChocolateHealthBonus.getOrDefault(playerId, 0.0) + 2.0);
                serverPlayer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            }
        }
        
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("item.chocomaker.chocolate.desc"));
    }

    /**
     * 检查玩家是否食用过巧克力
     * @param player 玩家
     * @return 如果玩家食用过巧克力返回true，否则返回false
     */
    public static boolean hasPlayerEatenChocolate(Player player) {
        return playerChocolateHealthBonus.containsKey(player.getUUID());
    }

    /**
     * 重置玩家的巧克力状态
     * @param player 玩家
     */
    public static void resetPlayerChocolateStatus(Player player) {
        UUID playerId = player.getUUID();
        if (playerChocolateHealthBonus.containsKey(playerId)) {
            double healthBonus = playerChocolateHealthBonus.get(playerId);
            playerChocolateHealthBonus.remove(playerId);
            if (player instanceof ServerPlayer serverPlayer) {
                double currentMaxHealth = serverPlayer.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
                serverPlayer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(currentMaxHealth - healthBonus);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ChocolateEventHandler {
        @SubscribeEvent
        public static void onPlayerDeath(LivingDeathEvent event) {
            if (!Configs.COMMON.resetChocolateOnDeath.get()) {
                return;
            }
            if (event.getEntity() instanceof Player player) {
                ChocolateItem.resetPlayerChocolateStatus(player);
            }
        }

        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (!Configs.COMMON.resetChocolateOnDeath.get()) {
                return;
            }
            Player player = event.getEntity();
            ChocolateItem.resetPlayerChocolateStatus(player);
        }
    }
}
