package com.emowolf.scm.inventory;

import com.emowolf.scm.blockentity.FoodReplicatorBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class FoodReplicatorMenu extends AbstractContainerMenu {

    public static final int PLAYER_INV_START = 2;
    private final FoodReplicatorBlockEntity blockEntity;
    private final ContainerData data;

    /** Server-side constructor */
    public FoodReplicatorMenu(int containerId, Inventory playerInventory, FoodReplicatorBlockEntity be) {
        super(SCMMenus.FOOD_REPLICATOR_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.data;
        addDataSlots(data);

        ItemStackHandler inv = be.getInventory();

        // Input slot (slot 0) — accepts food with saturation > 0
        addSlot(new SlotItemHandler(inv, FoodReplicatorBlockEntity.INPUT_SLOT, 44, 35) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                FoodProperties food = stack.getItem().getFoodProperties();
                return food != null && food.getSaturationModifier() > 0;
            }
        });

        // Output slot (slot 1) — output only
        addSlot(new SlotItemHandler(inv, FoodReplicatorBlockEntity.OUTPUT_SLOT, 116, 35) {
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
    public FoodReplicatorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, buf));
    }

    private static FoodReplicatorBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof FoodReplicatorBlockEntity repBE) return repBE;
        throw new IllegalStateException("No FoodReplicatorBlockEntity at " + buf.readBlockPos());
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    public FoodReplicatorBlockEntity getBlockEntity() {
        return blockEntity;
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
            // Moving from machine slots (input or output) to player inventory
            if (!moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Moving from player inventory to input slot (output slot rejects all)
            if (!moveItemStackTo(stack, 0, 1, false)) {
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
