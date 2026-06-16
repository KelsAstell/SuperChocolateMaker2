package com.emowolf.scm.client.screen;

import com.emowolf.scm.inventory.ChocolateMixerMenu;
import com.emowolf.scm.network.SCMNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ChocolateMixerScreen extends AbstractContainerScreen<ChocolateMixerMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("chocomaker", "textures/gui/container/chocolate_mixer.png");

    private Button mixButton;

    public ChocolateMixerScreen(ChocolateMixerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        // Mix button: aligned with output slot row
        mixButton = Button.builder(Component.translatable("container.chocomaker.chocolate_mixer.mix"), btn -> {
            SCMNetwork.CHANNEL.sendToServer(
                    new SCMNetwork.ChocolateMixerMixPacket(menu.getBlockEntity().getBlockPos()));
        }).bounds(leftPos + 44, topPos + 61, 70, 20).build();
        addRenderableWidget(mixButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // Preview: expected nutrition & saturation
        int previewNutrition = menu.getPreviewNutrition();
        float previewSaturation = menu.getPreviewSaturation();
        Component previewText = Component.translatable("container.chocomaker.chocolate_mixer.preview",
                previewNutrition, String.format("%.1f", previewSaturation));
        graphics.drawString(this.font, previewText, 8, 17, 0x00AA00, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
