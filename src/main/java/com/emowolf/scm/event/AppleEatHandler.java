package com.emowolf.scm.event;

import com.emowolf.scm.Configs;
import com.emowolf.scm.SCM;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class AppleEatHandler {
    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!Configs.COMMON.enableAnAppleADay.get()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        
        // 检查仪式是否已解锁
        if (!player.level().isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) player.level();
            RitualUnlockData data = RitualUnlockData.get(serverLevel);
            if (!data.isAppleEatUnlocked()) return;
        } else {
            return; // 客户端不处理
        }
        
        ItemStack stack = event.getItem();
        if (stack.is(Items.APPLE) || stack.is(Items.COOKIE)) {
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20f);
        }
    }
}
