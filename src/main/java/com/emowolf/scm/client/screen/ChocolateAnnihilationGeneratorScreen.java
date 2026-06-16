package com.emowolf.scm.client.screen;

import com.emowolf.scm.inventory.ChocolateAnnihilationGeneratorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.text.DecimalFormat;

public class ChocolateAnnihilationGeneratorScreen extends AbstractContainerScreen<ChocolateAnnihilationGeneratorMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("chocomaker", "textures/gui/container/chocolate_annihilation_generator.png");
    private static final DecimalFormat RATE_FORMAT = new DecimalFormat("#,##0.##");

    public ChocolateAnnihilationGeneratorScreen(ChocolateAnnihilationGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw energy bar (right side of GUI)
        long storedEnergy = menu.getStoredEnergy();
        long maxEnergy = 1_000_000_000L;
        // Scale: display as 0-1G mapped to 52px bar
        double energyPercent = Math.min((double) storedEnergy / maxEnergy, 1.0);
        int barHeight = (int) (energyPercent * 52);
        // Bar at x=148, y=17, height=52, fill from bottom
        graphics.fill(leftPos + 148, topPos + 17 + (52 - barHeight), leftPos + 148 + 16,
                topPos + 17 + 52, 0xFFFF4500); // orange-red for energy

        // Draw generation rate bar (left side of GUI)
        double maxDisplayRate = 100.0; // scale for visual display
        double ratePercent = Math.min(menu.getGenerationRate() / maxDisplayRate, 1.0);
        int rateBarHeight = (int) (ratePercent * 52);
        graphics.fill(leftPos + 12, topPos + 17 + (52 - rateBarHeight), leftPos + 12 + 8,
                topPos + 17 + 52, 0xFF8B4513); // chocolate brown
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // Stored energy display
        long storedEnergy = menu.getStoredEnergy();
        String energyText;
        if (storedEnergy >= 1_000_000_000L) {
            energyText = String.format("%.2f GFE", storedEnergy / 1_000_000_000.0);
        } else if (storedEnergy >= 1_000_000L) {
            energyText = String.format("%.2f MFE", storedEnergy / 1_000_000.0);
        } else if (storedEnergy >= 1_000L) {
            energyText = String.format("%.2f kFE", storedEnergy / 1_000.0);
        } else {
            energyText = storedEnergy + " FE";
        }
        graphics.drawString(this.font, Component.literal(energyText), 26, 18, 0x404040, false);

        // Generation rate
        String rateText = Component.translatable("gui.chocomaker.choco_gen.rate", RATE_FORMAT.format(menu.getGenerationRate())).getString();
        graphics.drawString(this.font, Component.literal(rateText), 26, 36, 0x404040, false);

        // Max storage hint
        graphics.drawString(this.font, Component.translatable("gui.chocomaker.choco_gen.max_storage", "1.00 GFE"),
                26, 54, 0x404040, false);

        // Charging slot indicator
        ItemStack chargeStack = this.menu.slots.get(1).getItem();
        if (!chargeStack.isEmpty()) {
            chargeStack.getCapability(ForgeCapabilities.ENERGY).ifPresent(energy -> {
                int stored = energy.getEnergyStored();
                int max = energy.getMaxEnergyStored();
                String chargeText;
                if (max >= 1_000_000) {
                    chargeText = String.format("%.1f/%.1f MFE", stored / 1_000_000.0, max / 1_000_000.0);
                } else if (max >= 1_000) {
                    chargeText = String.format("%.1f/%.1f kFE", stored / 1_000.0, max / 1_000.0);
                } else {
                    chargeText = stored + "/" + max + " FE";
                }
                graphics.drawString(this.font, Component.literal(chargeText), 26, 72, 0x404040, false);
            });
            graphics.drawString(this.font, Component.translatable("gui.chocomaker.choco_gen.charge"), 128, 56, 0x404040, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
