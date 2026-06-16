package com.emowolf.scm.client.screen;

import com.emowolf.scm.inventory.EternalBeaconMenu;
import com.emowolf.scm.network.SCMNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class EternalBeaconScreen extends AbstractContainerScreen<EternalBeaconMenu> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("chocomaker", "textures/gui/container/eternal_beacon.png");

    private static final String[] LEVEL_NAMES = {"I", "II", "III", "IV"};

    private final Button[] levelButtons = new Button[4];

    public EternalBeaconScreen(EternalBeaconMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        // Level selection buttons (4 buttons in a row, between potion slot and energy bar)
        for (int i = 0; i < 4; i++) {
            final int level = i;
            levelButtons[i] = Button.builder(
                    Component.literal(LEVEL_NAMES[i]),
                    btn -> onLevelSelected(level)
            ).bounds(leftPos + 80 + i * 17, topPos + 34, 16, 20).build();
            addRenderableWidget(levelButtons[i]);
        }
    }

    private void onLevelSelected(int level) {
        SCMNetwork.CHANNEL.sendToServer(
                new SCMNetwork.SetBeaconLevelPacket(menu.getBlockEntity().getBlockPos(), level)
        );
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Draw energy bar (right side of GUI) - display cap at 2000 for visualization
        long energy = menu.getChocolateEnergy();
        int displayMax = 2000;
        int barHeight = (int) (Math.min((float) energy / displayMax, 1.0f) * 52);
        // Draw bar from bottom up at x=148, y=17, height=52
        graphics.fill(leftPos + 148, topPos + 17 + (52 - barHeight), leftPos + 148 + 16,
                topPos + 17 + 52, 0xFF8B4513); // brown color for chocolate

        // Highlight selected level button
        int selectedLevel = menu.getSelectedLevel();
        for (int i = 0; i < 4; i++) {
            if (levelButtons[i] != null) {
                levelButtons[i].active = (i != selectedLevel);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        // Player inventory label
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        // Energy text - right-aligned to energy bar (bar spans x=148~164)
        String energyStr = String.valueOf(menu.getChocolateEnergy());
        int energyTextWidth = this.font.width(energyStr);
        graphics.drawString(this.font, Component.literal(energyStr),
                146 - energyTextWidth, 72, 0xFFFFFF, false);

        // Level label
        graphics.drawString(this.font, Component.translatable("gui.chocomaker.eternal_beacon.level"),
                80, 22, 0x404040, false);

        // Consumption info
        int level = menu.getSelectedLevel();
        graphics.drawString(this.font,
                Component.translatable("gui.chocomaker.eternal_beacon.level_selected", LEVEL_NAMES[level]),
                80, 60, 0x888888, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
