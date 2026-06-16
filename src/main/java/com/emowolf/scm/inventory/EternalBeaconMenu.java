package com.emowolf.scm.inventory;

import com.emowolf.scm.blockentity.EternalBeaconBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class EternalBeaconMenu extends AbstractContainerMenu {

    public static final int PLAYER_INV_START = 2;
    private final EternalBeaconBlockEntity blockEntity;

    // ContainerData for syncing energy and level
    private final ContainerData data;

    /** Server-side constructor */
    public EternalBeaconMenu(int containerId, Inventory playerInventory, EternalBeaconBlockEntity be) {
        super(SCMMenus.ETERNAL_BEACON_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.data;
        addDataSlots(data);

        ItemStackHandler inv = be.getInventory();

        // Slot 0: Potion slot (requires energy to place)
        addSlot(new SlotItemHandler(inv, EternalBeaconBlockEntity.POTION_SLOT, 56, 34) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.getItem() instanceof PotionItem && blockEntity.getChocolateEnergy() > 0;
            }
        });

        // Slot 1: Chocolate slot
        addSlot(new SlotItemHandler(inv, EternalBeaconBlockEntity.CHOCOLATE_SLOT, 56, 58));

        // Player inventory (slots 2-28)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar (slots 29-37)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    /** Client-side constructor (from network) */
    public EternalBeaconMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, buf));
    }

    private static EternalBeaconBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof EternalBeaconBlockEntity beaconBE) return beaconBE;
        throw new IllegalStateException("No EternalBeaconBlockEntity at " + buf.readBlockPos());
    }

    public long getChocolateEnergy() {
        long low = data.get(0) & 0xFFFFFFFFL;
        long high = data.get(1) & 0xFFFFFFFFL;
        return (high << 32) | low;
    }

    public int getSelectedLevel() {
        return data.get(2);
    }

    public EternalBeaconBlockEntity getBlockEntity() {
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
            // Moving from machine slots to player inventory
            if (!moveItemStackTo(stack, PLAYER_INV_START, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Moving from player inventory to machine slots
            if (stack.getItem() instanceof PotionItem) {
                if (!moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
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
