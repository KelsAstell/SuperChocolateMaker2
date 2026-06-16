package com.emowolf.scm.client;

import com.emowolf.scm.Configs;
import com.emowolf.scm.SCM;
import com.emowolf.scm.item.DimensionalShieldItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DimensionalShieldHud {

    private static final String CURIOS_MODID = "curios";

    // 进度条尺寸
    private static final int BAR_WIDTH = 144;
    private static final int BAR_HEIGHT = 10;
    private static final int BORDER = 1;

    // 颜色常量
    private static final int COLOR_BG = 0x40FFFFFF;      // 半透明白色背景
    private static final int COLOR_FILL = 0xFFFFFFFF;    // 纯白填充
    private static final int COLOR_BORDER = 0xFFAAAAAA;  // 浅灰边框
    private static final int COLOR_TEXT = 0xFFFFFFFF;    // 白色文字
    private static final int COLOR_TEXT_DECREASING = 0xFFFFFF00;  // 黄色文字（护盾下降时）
    private static final int COLOR_LOAD_LOW = 0xFFFFA500;   // 橙色（负载 > 50）
    private static final int COLOR_LOAD_HIGH = 0xFFFF4444;  // 红色（负载 > 80）

    // 上一帧的护盾值，用于检测下降
    private static double lastShieldValue = -1;

    // === 客户端平滑插值（每帧逼近服务端目标值，消除每秒1次的跳变感） ===
    /** 平滑后的显示护盾值，每帧向服务端目标值 lerp */
    private static double displayShieldValue = -1;
    /** 平滑后的显示负载值 */
    private static double displayLoad = -1;
    /** 平滑后的显示回复速率 */
    private static double displayRegenRate = -1;
    /** 平滑因子：每帧逼近目标的比例，值越大越灵敏（0.15 ≈ 约0.3秒内收敛到95%） */
    private static final double SMOOTH_FACTOR = 0.15;

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CHAT_PANEL.id(), "dimensional_shield",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
                    renderShieldHud(guiGraphics, screenWidth, screenHeight);
                });
    }

    private static void renderShieldHud(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!ModList.get().isLoaded(CURIOS_MODID)) return;

        ItemStack shield = findClientShield(mc);
        if (shield == null) return;

        double targetShieldValue = DimensionalShieldItem.getShieldValue(shield);
        double maxShield = DimensionalShieldItem.getMaxShield(shield);
        double targetRegenRate = DimensionalShieldItem.getRegenRate(shield);
        int targetLoad = DimensionalShieldItem.getLoad(shield);

        // === 客户端平滑插值：每帧向服务端目标值逼近，消除每秒1次更新的跳变感 ===
        if (displayShieldValue < 0) {
            // 首帧直接设为目标值
            displayShieldValue = targetShieldValue;
            displayLoad = targetLoad;
            displayRegenRate = targetRegenRate;
        } else {
            displayShieldValue += (targetShieldValue - displayShieldValue) * SMOOTH_FACTOR;
            displayLoad += (targetLoad - displayLoad) * SMOOTH_FACTOR;
            displayRegenRate += (targetRegenRate - displayRegenRate) * SMOOTH_FACTOR;
        }

        double shieldValue = displayShieldValue;
        double regenRate = displayRegenRate;
        int load = (int) Math.round(displayLoad);

        Font font = mc.font;
        int barX = Configs.CLIENT.hudOffsetX.get();
        int barY = screenHeight - Configs.CLIENT.hudOffsetY.get();

        // === 进度条背景 ===
        guiGraphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, COLOR_BG);

        // === 进度条填充 ===
        if (shieldValue > 0 && maxShield > 0) {
            double ratio = Math.min(shieldValue / maxShield, 1.0);
            int fillWidth = (int) (ratio * (BAR_WIDTH - BORDER * 2));
            if (fillWidth > 0) {
                guiGraphics.fill(
                        barX + BORDER, barY + BORDER,
                        barX + BORDER + fillWidth, barY + BAR_HEIGHT - BORDER,
                        COLOR_FILL
                );
            }
        }

        // === 白色边框（四条细线） ===
        // 上边框
        guiGraphics.fill(barX, barY, barX + BAR_WIDTH, barY + BORDER, COLOR_BORDER);
        // 下边框
        guiGraphics.fill(barX, barY + BAR_HEIGHT - BORDER, barX + BAR_WIDTH, barY + BAR_HEIGHT, COLOR_BORDER);
        // 左边框
        guiGraphics.fill(barX, barY, barX + BORDER, barY + BAR_HEIGHT, COLOR_BORDER);
        // 右边框
        guiGraphics.fill(barX + BAR_WIDTH - BORDER, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, COLOR_BORDER);

        // === 文字：值/上限 (+回复速率/s) [负载] ===
        boolean decreasing = (lastShieldValue >= 0 && shieldValue < lastShieldValue);
        int textColor = decreasing ? COLOR_TEXT_DECREASING : COLOR_TEXT;
        String text = DimensionalShieldItem.formatCompact(shieldValue) + " / " + DimensionalShieldItem.formatCompact(maxShield)
                + "  (+" + DimensionalShieldItem.formatCompact(regenRate) + "/s)";
        int textY = barY + BAR_HEIGHT + 3;
        guiGraphics.drawString(font, text, barX, textY, textColor, true);

        // 负载值（有负载时才显示）
        if (load > 0) {
            int loadColor = load > 80 ? COLOR_LOAD_HIGH : (load > 50 ? COLOR_LOAD_LOW : COLOR_TEXT);
            String loadText = "Load: " + load + "/" + DimensionalShieldItem.MAX_LOAD;
            guiGraphics.drawString(font, loadText, barX, textY + font.lineHeight + 2, loadColor, true);
        }

        lastShieldValue = shieldValue;
    }


    @SuppressWarnings("DataFlowIssue")
    private static ItemStack findClientShield(Minecraft mc) {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(mc.player)
                .map(handler -> handler.findFirstCurio(SCMItems.DIMENSIONAL_SHIELD.get()))
                .orElse(java.util.Optional.empty())
                .map(r -> r.stack())
                .orElse(null);
    }
}
