package com.emowolf.scm.client.screen;

import com.emowolf.scm.blockentity.HyperCannonControlCenterBlockEntity;
import com.emowolf.scm.inventory.HyperCannonControlCenterMenu;
import com.emowolf.scm.network.SCMNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class HyperCannonControlCenterScreen extends AbstractContainerScreen<HyperCannonControlCenterMenu> {

    private static final String[] MODE_NAMES = {"arrow", "light_spear", "explosion"};

    // Terminal color palette
    private static final int CLR_BG_OUTER       = 0xE0050A05;  // near-black with green tint
    private static final int CLR_BORDER         = 0xFF0A3A0A;  // dark green border
    private static final int CLR_BG_INNER       = 0xFF0A140A;  // very dark green-black
    private static final int CLR_TITLE_BAR      = 0xFF0A2A0A;  // dark green title
    private static final int CLR_SLOT_BG        = 0xFF0A1A0A;  // slot inner
    private static final int CLR_TEXT_BRIGHT    = 0xFF00FF00;  // bright green
    private static final int CLR_TEXT_DIM       = 0xFF00AA00;  // medium green
    private static final int CLR_TEXT_FAINT     = 0xFF006600;  // dim green
    private static final int CLR_ENERGY_BAR     = 0xFF00CC00;  // green energy
    private static final int CLR_ALERT          = 0xFFFF3300;  // red-orange alert
    private static final int CLR_BTN_BG         = 0xFF060E06;  // button background
    private static final int CLR_BTN_HOVER      = 0xFF0A1E0A;  // button hover
    private static final int CLR_BTN_SEL_BG     = 0xFF0A3A0A;  // selected button bg
    private static final int CLR_BTN_SEL_BORDER = 0xFF00CC00;  // selected button border
    private static final int CLR_BTN_FIRE_BG    = 0xFF051005;  // fire button bg
    private static final int CLR_BTN_FIRE_HOVER = 0xFF0A2A0A;  // fire button hover
    private static final int CLR_BTN_FIRE_BORDER= 0xFF00AA00;  // fire button border

    private EditBox inputX;
    private EditBox inputY;
    private EditBox inputZ;
    private int selectedMode = 0;
    private TerminalButton[] modeButtons = new TerminalButton[3];
    private TerminalButton fireButton;

    public HyperCannonControlCenterScreen(HyperCannonControlCenterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        int inputLeft = leftPos + 28;
        int inputWidth = 55;
        int inputHeight = 16;

        // X coordinate input
        inputX = new EditBox(this.font, inputLeft, topPos + 18, inputWidth, inputHeight, Component.literal("X"));
        inputX.setValue(String.valueOf(menu.getTargetX()));
        inputX.setFilter(s -> s.matches("-?\\d*"));
        inputX.setTextColor(CLR_TEXT_BRIGHT);
        addRenderableWidget(inputX);

        // Y coordinate input
        inputY = new EditBox(this.font, inputLeft, topPos + 36, inputWidth, inputHeight, Component.literal("Y"));
        inputY.setValue(String.valueOf(menu.getTargetY()));
        inputY.setFilter(s -> s.matches("-?\\d*"));
        inputY.setTextColor(CLR_TEXT_BRIGHT);
        addRenderableWidget(inputY);

        // Z coordinate input
        inputZ = new EditBox(this.font, inputLeft, topPos + 54, inputWidth, inputHeight, Component.literal("Z"));
        inputZ.setValue(String.valueOf(menu.getTargetZ()));
        inputZ.setFilter(s -> s.matches("-?\\d*"));
        inputZ.setTextColor(CLR_TEXT_BRIGHT);
        addRenderableWidget(inputZ);

        // Mode selection buttons
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            modeButtons[i] = new TerminalButton(
                    leftPos + 8 + i * 55, topPos + 66, 52, 20,
                    Component.translatable("gui.chocomaker.hyper_cannon.mode." + MODE_NAMES[i]),
                    btn -> selectMode(mode),
                    false
            );
            addRenderableWidget(modeButtons[i]);
        }

        // Fire button
        fireButton = new TerminalButton(
                leftPos + 48, topPos + 90, 80, 20,
                Component.translatable("gui.chocomaker.hyper_cannon.fire"),
                btn -> onFire(),
                true
        );
        addRenderableWidget(fireButton);

        // Sync initial mode
        selectedMode = menu.getMode();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            if (inputX.isFocused() || inputY.isFocused() || inputZ.isFocused()) {
                inputX.setFocused(false);
                inputY.setFocused(false);
                inputZ.setFocused(false);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== Rendering ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        // Hide chocolate fuel slot (index 0) by temporarily moving it off-screen.
        // renderSlot is private in AbstractContainerScreen on Forge 1.20.1, so we
        // can't override it; instead we shift the slot position before super.render().
        net.minecraft.world.inventory.Slot hiddenSlot = this.menu.slots.get(0);
        int savedX = getSlotX(hiddenSlot);
        int savedY = getSlotY(hiddenSlot);
        setSlotPos(hiddenSlot, -9999, -9999);

        super.render(graphics, mouseX, mouseY, partialTick);

        setSlotPos(hiddenSlot, savedX, savedY);

        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Reflectively read Slot.x (may be final in some mappings). */
    private static int getSlotX(net.minecraft.world.inventory.Slot slot) {
        try {
            var field = net.minecraft.world.inventory.Slot.class.getDeclaredField("x");
            field.setAccessible(true);
            return field.getInt(slot);
        } catch (Exception e) {
            return slot.x;
        }
    }

    /** Reflectively read Slot.y (may be final in some mappings). */
    private static int getSlotY(net.minecraft.world.inventory.Slot slot) {
        try {
            var field = net.minecraft.world.inventory.Slot.class.getDeclaredField("y");
            field.setAccessible(true);
            return field.getInt(slot);
        } catch (Exception e) {
            return slot.y;
        }
    }

    /** Reflectively set Slot x/y, stripping final modifier if needed. */
    private static void setSlotPos(net.minecraft.world.inventory.Slot slot, int newX, int newY) {
        try {
            // Try direct field access first (works on non-final mapped fields)
            var fx = net.minecraft.world.inventory.Slot.class.getDeclaredField("x");
            var fy = net.minecraft.world.inventory.Slot.class.getDeclaredField("y");
            fx.setAccessible(true);
            fy.setAccessible(true);
            // Strip final modifier for remapped environments
            var modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(fx, fx.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            modifiersField.setInt(fy, fy.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
            fx.setInt(slot, newX);
            fy.setInt(slot, newY);
        } catch (Exception e) {
            // Fallback: try public setter or ignore
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Draw dark background (full height to cover player inventory area)
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, CLR_BG_OUTER);
        // Main panel inner background (control area)
        graphics.fill(leftPos + 7, topPos + 16, leftPos + 169, topPos + 112, CLR_BORDER);
        graphics.fill(leftPos + 8, topPos + 17, leftPos + 168, topPos + 111, CLR_BG_INNER);
        // Player inventory section background
        graphics.fill(leftPos + 7, topPos + 112, leftPos + 169, topPos + 216, CLR_BORDER);
        graphics.fill(leftPos + 8, topPos + 113, leftPos + 168, topPos + 215, CLR_BG_INNER);



        // Title area
        graphics.fill(leftPos + 7, topPos + 4, leftPos + 169, topPos + 15, CLR_TITLE_BAR);

        // Input field backgrounds
        int inputLeft = leftPos + 28;
        graphics.fill(inputLeft - 1, topPos + 17, inputLeft + 56, topPos + 35, 0xFF000000);
        graphics.fill(inputLeft - 1, topPos + 35, inputLeft + 56, topPos + 53, 0xFF000000);
        graphics.fill(inputLeft - 1, topPos + 53, inputLeft + 56, topPos + 71, 0xFF000000);

        // Energy bar background
        graphics.fill(leftPos + 148, topPos + 17, leftPos + 164, topPos + 59, 0xFF0A1A0A);

        // Energy bar fill
        long energy = menu.getEnergy();
        long displayMax = Math.max(1000, energy);
        int barHeight = (int) (Math.min((double) energy / displayMax, 1.0) * 40);
        int barColor = menu.isExplosionActive() || menu.isLightSpearActive() ? CLR_ALERT : CLR_ENERGY_BAR;
        graphics.fill(leftPos + 149, topPos + 18 + (40 - barHeight), leftPos + 163,
                topPos + 18 + 40, barColor);

        // Update mode button selection states
        for (int i = 0; i < 3; i++) {
            if (modeButtons[i] != null) {
                modeButtons[i].selected = (i == selectedMode);
                modeButtons[i].active = !menu.isExplosionActive() && !menu.isLightSpearActive();
            }
        }

        // Fire button active state
        if (fireButton != null) {
            fireButton.active = !menu.isExplosionActive() && !menu.isLightSpearActive();
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Title
        graphics.drawString(this.font, this.title, this.titleLabelX, 6, CLR_TEXT_BRIGHT, false);

        // Coordinate labels
        graphics.drawString(this.font, Component.literal("X:"), 10, 22, CLR_TEXT_BRIGHT, false);
        graphics.drawString(this.font, Component.literal("Y:"), 10, 40, CLR_TEXT_BRIGHT, false);
        graphics.drawString(this.font, Component.literal("Z:"), 10, 58, CLR_TEXT_BRIGHT, false);

        // Mode label (positioned next to mode buttons, x after buttons)
        // Buttons occupy x=8~170 at y=66~86, label is implicit via button text

        // Energy label
        graphics.drawString(this.font, Component.translatable("gui.chocomaker.hyper_cannon.energy"),
                90, 22, CLR_TEXT_DIM, false);

        // Energy value
        long energy = menu.getEnergy();
        String energyText;
        if (energy >= 1_000_000) {
            energyText = String.format("%.1f M", energy / 1_000_000.0);
        } else if (energy >= 1_000) {
            energyText = String.format("%.1f k", energy / 1_000.0);
        } else {
            energyText = String.valueOf(energy);
        }
        graphics.drawString(this.font, Component.literal(energyText), 90, 34, CLR_TEXT_BRIGHT, false);

        // Estimated cost (read live from EditBox for real-time update)
        int displayTargetX = parseCoord(inputX, menu.getTargetX());
        int displayTargetZ = parseCoord(inputZ, menu.getTargetZ());
        int cost = HyperCannonControlCenterBlockEntity.calculateCost(
                menu.getBlockEntity().getBlockPos().getX(),
                menu.getBlockEntity().getBlockPos().getZ(),
                displayTargetX, displayTargetZ, selectedMode);
        graphics.drawString(this.font, Component.translatable("gui.chocomaker.hyper_cannon.cost", cost),
                90, 52, CLR_TEXT_FAINT, false);

        // Status
        if (menu.isExplosionActive()) {
            graphics.drawString(this.font, Component.translatable("gui.chocomaker.hyper_cannon.firing"),
                    10, 112, CLR_ALERT, false);
        } else if (menu.isLightSpearActive()) {
            graphics.drawString(this.font, Component.translatable("gui.chocomaker.hyper_cannon.charging"),
                    10, 112, CLR_ALERT, false);
        }

        // Player inventory label
        graphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, 127, CLR_TEXT_DIM, false);
    }


    // ==================== Business Logic ====================

    /** Parse an EditBox as int, returning fallback if empty or invalid. */
    private static int parseCoord(EditBox box, int fallback) {
        if (box == null) return fallback;
        String text = box.getValue();
        if (text.isEmpty() || text.equals("-")) return fallback;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void selectMode(int mode) {
        selectedMode = mode;
    }

    private void onFire() {
        int x, y, z;
        try {
            x = Integer.parseInt(inputX.getValue());
            y = Integer.parseInt(inputY.getValue());
            z = Integer.parseInt(inputZ.getValue());
        } catch (NumberFormatException e) {
            return;
        }

        SCMNetwork.CHANNEL.sendToServer(
                new SCMNetwork.HyperCannonFirePacket(menu.getBlockEntity().getBlockPos(), x, y, z, selectedMode)
        );
    }

    // ==================== Terminal Themed Button ====================

    private static class TerminalButton extends Button {
        private final boolean isFireButton;
        boolean selected;

        TerminalButton(int x, int y, int width, int height, Component message,
                       OnPress onPress, boolean isFireButton) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.isFireButton = isFireButton;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean hovered = isHoveredOrFocused();

            int bgColor, borderColor, textColor;

            if (isFireButton) {
                // Fire button: stronger border, distinct look
                borderColor = CLR_BTN_FIRE_BORDER;
                if (!active) {
                    bgColor = CLR_BG_INNER;
                    textColor = CLR_TEXT_FAINT;
                } else if (hovered) {
                    bgColor = CLR_BTN_FIRE_HOVER;
                    textColor = CLR_TEXT_BRIGHT;
                } else {
                    bgColor = CLR_BTN_FIRE_BG;
                    textColor = CLR_TEXT_DIM;
                }
            } else {
                // Mode buttons
                if (selected) {
                    bgColor = CLR_BTN_SEL_BG;
                    borderColor = CLR_BTN_SEL_BORDER;
                    textColor = CLR_TEXT_BRIGHT;
                } else if (!active) {
                    bgColor = CLR_BG_INNER;
                    borderColor = CLR_BORDER;
                    textColor = CLR_TEXT_FAINT;
                } else if (hovered) {
                    bgColor = CLR_BTN_HOVER;
                    borderColor = CLR_BORDER;
                    textColor = CLR_TEXT_BRIGHT;
                } else {
                    bgColor = CLR_BTN_BG;
                    borderColor = CLR_BORDER;
                    textColor = CLR_TEXT_DIM;
                }
            }

            // Background
            graphics.fill(x, y, x + w, y + h, bgColor);
            // Border (1px on each side)
            graphics.fill(x, y, x + w, y + 1, borderColor);           // top
            graphics.fill(x, y + h - 1, x + w, y + h, borderColor);   // bottom
            graphics.fill(x, y, x + 1, y + h, borderColor);           // left
            graphics.fill(x + w - 1, y, x + w, y + h, borderColor);   // right

            // Text centered
            Component displayMsg = getMessage();
            int textWidth = Minecraft.getInstance().font.width(displayMsg);
            int textX = x + (w - textWidth) / 2;
            int textY = y + (h - 8) / 2;
            graphics.drawString(Minecraft.getInstance().font, displayMsg, textX, textY, textColor, false);
        }
    }
}
