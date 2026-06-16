package com.emowolf.scm.client.screen;

import com.emowolf.scm.inventory.FoodReplicatorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class FoodReplicatorScreen extends AbstractContainerScreen<FoodReplicatorMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("chocomaker", "textures/gui/container/food_replicator.png");

    public FoodReplicatorScreen(FoodReplicatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw progress arrow
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0) {
            int arrowWidth = (int) ((double) progress / maxProgress * 24);
            if (arrowWidth > 0) {
                // Green progress fill over the arrow area (x=71, y=35, maxW=24, h=17)
                graphics.fill(leftPos + 71, topPos + 35, leftPos + 71 + arrowWidth, topPos + 35 + 17, 0xFF00CC00);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // Progress text
        int progress = menu.getProgress();
        int maxProgress = menu.getMaxProgress();
        if (maxProgress > 0) {
            String progressText = progress + " / " + maxProgress;
            int textWidth = this.font.width(progressText);
            graphics.drawString(this.font, Component.literal(progressText), (imageWidth - textWidth) / 2, 58, 0xAAAAAA, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
