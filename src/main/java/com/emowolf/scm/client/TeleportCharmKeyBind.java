package com.emowolf.scm.client;

import com.emowolf.scm.SCM;
import com.emowolf.scm.network.SCMNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端快捷键：按下默认0键触发传送护符传送
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, value = Dist.CLIENT)
public class TeleportCharmKeyBind {

    public static final KeyMapping TELEPORT_KEY = new KeyMapping(
            "key.chocomaker.teleport_charm",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_0,
            "key.categories.scm"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TELEPORT_KEY);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 只在按键按下时触发（不处理重复）
        if (TELEPORT_KEY.consumeClick()) {
            SCMNetwork.CHANNEL.sendToServer(new SCMNetwork.TeleportCharmPacket());
        }
    }
}
