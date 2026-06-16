package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.item.TeleportCharmItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 处理右键点击玩家实体时绑定传送护符目标
 */
@Mod.EventBusSubscriber(modid = SCM.MODID)
public class TeleportCharmBindHandler {

    @SubscribeEvent
    public static void onPlayerInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Player targetPlayer)) return;

        Player user = event.getEntity();
        ItemStack heldItem = user.getItemInHand(event.getHand());

        if (heldItem.getItem() instanceof TeleportCharmItem) {
            InteractionResult result = TeleportCharmItem.bindToPlayer(heldItem, user, targetPlayer);
            if (result == InteractionResult.SUCCESS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        }
    }
}
