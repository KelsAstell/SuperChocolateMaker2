package com.emowolf.scm.client;

import com.emowolf.scm.SCM;
import com.emowolf.scm.item.BottledRainbowItem;
import com.emowolf.scm.item.SCMItems;
import com.emowolf.scm.network.SCMNetwork;
import com.emowolf.scm.network.BottledRainbowScrollPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件：潜行时手持瓶装彩虹，滚轮切换目标颜色
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, value = Dist.CLIENT)
public class BottledRainbowScrollHandler {

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !player.isShiftKeyDown()) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(SCMItems.BOTTLED_RAINBOW.get())) {
            int delta = event.getScrollDelta() > 0 ? 1 : -1;

            // 客户端本地预计算新颜色，立即在操作栏显示
            DyeColor currentColor = BottledRainbowItem.getColor(mainHand);
            int newColorId = ((currentColor.getId() + delta) % 16 + 16) % 16;
            DyeColor newColor = DyeColor.byId(newColorId);
            player.displayClientMessage(
                    Component.translatable("item.chocomaker.bottled_rainbow.color",
                            Component.translatable("color.chocomaker." + newColor.getName())),
                    true);

            SCMNetwork.CHANNEL.sendToServer(new BottledRainbowScrollPacket(delta));
            event.setCanceled(true);
        }
    }
}
