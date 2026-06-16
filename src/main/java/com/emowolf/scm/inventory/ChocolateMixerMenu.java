package com.emowolf.scm.inventory;

import com.emowolf.scm.blockentity.ChocolateMixerBlockEntity;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class ChocolateMixerMenu extends AbstractContainerMenu {

    public static final int PLAYER_INV_START = ChocolateMixerBlockEntity.TOTAL_SLOTS;
    private final ChocolateMixerBlockEntity blockEntity;
    private final ContainerData data;

    /** Server-side constructor */
    public ChocolateMixerMenu(int containerId, Inventory playerInventory, ChocolateMixerBlockEntity be) {
        super(SCMMenus.CHOCOLATE_MIXER_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.data;
        addDataSlots(data);

        ItemStackHandler inv = be.getInventory();

        // Chocolate input slot (slot 0)
        addSlot(new SlotItemHandler(inv, ChocolateMixerBlockEntity.CHOCOLATE_SLOT, 44, 25) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.is(SCMItems.CHOCOLATE.get());
            }
        });

        // Ingredient slots: row 1 (slots 1-4)
        for (int i = 0; i < 4; i++) {
            final int slotIndex = ChocolateMixerBlockEntity.FIRST_INGREDIENT_SLOT + i;
            addSlot(new SlotItemHandler(inv, slotIndex, 62 + i * 18, 25) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return !stack.is(SCMItems.CHOCOLATE.get())
                            && !stack.is(SCMItems.MIXED_CHOCOLATE.get());
                }
            });
        }

        // Ingredient slots: row 2 (slots 5-8)
        for (int i = 0; i < 4; i++) {
            final int slotIndex = ChocolateMixerBlockEntity.FIRST_INGREDIENT_SLOT + 4 + i;
            addSlot(new SlotItemHandler(inv, slotIndex, 62 + i * 18, 43) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return !stack.is(SCMItems.CHOCOLATE.get())
                            && !stack.is(SCMItems.MIXED_CHOCOLATE.get());
                }
            });
        }

        // Output slot (slot 9)
        addSlot(new SlotItemHandler(inv, ChocolateMixerBlockEntity.OUTPUT_SLOT, 134, 61) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });

        // Player inventory (rows 0-2)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (row 3)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Client-side constructor (from network) */
    public ChocolateMixerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, buf));
    }

    private static ChocolateMixerBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof ChocolateMixerBlockEntity mixerBE) return mixerBE;
        throw new IllegalStateException("No ChocolateMixerBlockEntity at " + buf.readBlockPos());
    }

    public ChocolateMixerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isMixing() {
        return data.get(0) == 1;
    }

    public int getPreviewNutrition() {
        return data.get(1);
    }

    public float getPreviewSaturation() {
        return data.get(2) / 10.0f;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        double reach = player.getBlockReach();
        return player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5) <= Math.max(64.0, reach * reach);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < PLAYER_INV_START) {
            // Moving from machine slot to player inventory
            if (!moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Moving from player inventory to machine slots
            // Try chocolate slot first, then ingredient slots
            if (stack.is(SCMItems.CHOCOLATE.get())) {
                if (!moveItemStackTo(stack, ChocolateMixerBlockEntity.CHOCOLATE_SLOT,
                        ChocolateMixerBlockEntity.CHOCOLATE_SLOT + 1, false)) {
                    if (!moveItemStackTo(stack, ChocolateMixerBlockEntity.FIRST_INGREDIENT_SLOT,
                            ChocolateMixerBlockEntity.LAST_INGREDIENT_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            } else if (!stack.is(SCMItems.MIXED_CHOCOLATE.get())) {
                if (!moveItemStackTo(stack, ChocolateMixerBlockEntity.FIRST_INGREDIENT_SLOT,
                        ChocolateMixerBlockEntity.LAST_INGREDIENT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }
}
