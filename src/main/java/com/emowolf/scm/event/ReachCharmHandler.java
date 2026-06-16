package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.item.ReachCharmItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class ReachCharmHandler {

    /**
     * 拾取金巧克力时，若玩家主手持有距离护符，则自动吸收。
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemEntity itemEntity = event.getItem();
        ItemStack pickupStack = itemEntity.getItem();

        // 只吸收金巧克力
        if (!pickupStack.is(SCMItems.GOLDEN_CHOCOLATE.get())) return;

        // 仅主手持有护符时才吸收
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(SCMItems.REACH_CHARM.get())) return;

        int absorbed = ReachCharmItem.absorbGoldenChocolate(mainHand, pickupStack);

        if (absorbed > 0) {
            itemEntity.discard();
            event.setCanceled(true);

            double totalBonus = ReachCharmItem.getTotalBonus(mainHand);
            serverPlayer.sendSystemMessage(Component.translatable(
                    "msg.chocomaker.reach_charm.absorbed",
                    String.format("%.1f", totalBonus)));
        }
    }
}
