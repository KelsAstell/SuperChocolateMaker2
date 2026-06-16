package com.emowolf.scm.inventory;

import com.emowolf.scm.blockentity.ChocolateAnnihilationGeneratorBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class ChocolateAnnihilationGeneratorMenu extends AbstractContainerMenu {

    public static final int PLAYER_INV_START = 2;
    private final ChocolateAnnihilationGeneratorBlockEntity blockEntity;
    private final ContainerData data;

    /** Server-side constructor */
    public ChocolateAnnihilationGeneratorMenu(int containerId, Inventory playerInventory, ChocolateAnnihilationGeneratorBlockEntity be) {
        super(SCMMenus.CHOCOLATE_ANNIHILATION_GENERATOR_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.data;
        addDataSlots(data);

        ItemStackHandler inv = be.getInventory();

        // Slot 0: Chocolate / Chocolate Battery input (top-right corner)
        addSlot(new SlotItemHandler(inv, ChocolateAnnihilationGeneratorBlockEntity.CHOCOLATE_SLOT, 150, 16) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.is(com.emowolf.scm.item.SCMItems.CHOCOLATE.get()) || stack.is(com.emowolf.scm.item.SCMItems.CHOCOLATE_BATTERY.get());
            }
        });

        // Slot 1: Charging slot — accepts items with FE energy capability
        addSlot(new SlotItemHandler(inv, ChocolateAnnihilationGeneratorBlockEntity.CHARGE_SLOT, 150, 52) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.getCapability(ForgeCapabilities.ENERGY).map(storage -> storage.canReceive()).orElse(false);
            }
        });

        // Player inventory (slots 1-27)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (slots 28-36)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Client-side constructor (from network) */
    public ChocolateAnnihilationGeneratorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, buf));
    }

    private static ChocolateAnnihilationGeneratorBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof ChocolateAnnihilationGeneratorBlockEntity genBE) return genBE;
        throw new IllegalStateException("No ChocolateAnnihilationGeneratorBlockEntity at " + buf.readBlockPos());
    }

    public long getStoredEnergy() {
        long low = data.get(0) & 0xFFFFFFFFL;
        long high = data.get(1) & 0xFFFFFFFFL;
        return (high << 32) | low;
    }

    public double getGenerationRate() {
        long low = data.get(2) & 0xFFFFFFFFL;
        long high = data.get(3) & 0xFFFFFFFFL;
        return Double.longBitsToDouble((high << 32) | low);
    }

    public ChocolateAnnihilationGeneratorBlockEntity getBlockEntity() {
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
            // Moving from machine slot to player inventory
            if (!moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Moving from player inventory to machine slots
            // Try charge slot first, then chocolate slot
            if (this.slots.get(1).mayPlace(stack) && !moveItemStackTo(stack, 1, 2, false)) {
                if (!moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 1, false)) {
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
