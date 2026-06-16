package com.emowolf.scm.client;

import com.emowolf.scm.SCM;
import com.emowolf.scm.item.MachineGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 可可机枪潜行 HUD — 在准星上方显示蓄力点数、剩余能量。
 * 仅在潜行 + 手持可可机枪时显示。
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MachineGunHud {

    // 颜色常量
    private static final int COLOR_ENERGY   = 0xFFFFFF00; // 黄色 — 能量
    private static final int COLOR_CHARGE   = 0xFFFF5555; // 红色 — 蓄力
    private static final int COLOR_DAMAGE   = 0xFFFFAA00; // 橙色 — 伤害预览
    private static final int COLOR_WARNING  = 0xFFFF4444; // 红色 — 能量不足警告

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "machine_gun_hud",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
                    renderHud(guiGraphics, screenWidth, screenHeight);
                });
    }

    private static void renderHud(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.isCrouching()) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof MachineGunItem)) return;

        long energy = MachineGunItem.getEnergy(stack);
        long beamCharge = MachineGunItem.getBeamCharge(stack);
        double damage = beamCharge * MachineGunItem.BEAM_DAMAGE_PER_ENERGY;

        Font font = mc.font;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // 从准星上方 24 像素开始向上绘制
        int y = centerY - 24;

        // 第一行：蓄力点数 + 伤害预览（有蓄力时才显示）
        if (beamCharge > 0) {
            Component chargeLine = Component.translatable(
                    "hud.chocomaker.machine_gun.charge", beamCharge, (int) damage);
            int textWidth = font.width(chargeLine);
            guiGraphics.drawString(font, chargeLine, centerX - textWidth / 2, y, COLOR_CHARGE, true);
            y -= font.lineHeight + 2;
        }

        // 第二行：剩余能量 / 能量不足警告
        if (energy <= 0) {
            Component warning = Component.translatable("hud.chocomaker.machine_gun.no_energy");
            int textWidth = font.width(warning);
            guiGraphics.drawString(font, warning, centerX - textWidth / 2, y, COLOR_WARNING, true);
        } else {
            Component energyLine = Component.translatable(
                    "hud.chocomaker.machine_gun.energy", energy);
            int textWidth = font.width(energyLine);
            guiGraphics.drawString(font, energyLine, centerX - textWidth / 2, y, COLOR_ENERGY, true);
        }
    }
}
