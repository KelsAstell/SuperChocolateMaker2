package com.emowolf.scm.inventory;

import com.emowolf.scm.blockentity.HyperCannonControlCenterBlockEntity;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class HyperCannonControlCenterMenu extends AbstractContainerMenu {

    public static final int PLAYER_INV_START = 1;
    private final HyperCannonControlCenterBlockEntity blockEntity;
    private final ContainerData data;

    /** Server-side constructor */
    public HyperCannonControlCenterMenu(int containerId, Inventory playerInventory,
                                         HyperCannonControlCenterBlockEntity be) {
        super(SCMMenus.HYPER_CANNON_CONTROL_CENTER_MENU.get(), containerId);
        this.blockEntity = be;
        this.data = be.data;
        addDataSlots(data);

        ItemStackHandler inv = be.getInventory();

        // Slot 0: Chocolate / Chocolate Battery fuel input
        addSlot(new SlotItemHandler(inv, HyperCannonControlCenterBlockEntity.CHOCOLATE_SLOT, 80, 36) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return stack.is(SCMItems.CHOCOLATE.get()) || stack.is(SCMItems.CHOCOLATE_BATTERY.get());
            }
        });

        // Player inventory (slots 1-27)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 138 + row * 18));
            }
        }

        // Player hotbar (slots 28-36)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 196));
        }
    }

    /** Client-side constructor (from network) */
    public HyperCannonControlCenterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, buf));
    }

    private static HyperCannonControlCenterBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf buf) {
        BlockEntity be = inv.player.level().getBlockEntity(buf.readBlockPos());
        if (be instanceof HyperCannonControlCenterBlockEntity cannonBE) return cannonBE;
        throw new IllegalStateException("No HyperCannonControlCenterBlockEntity at " + buf.readBlockPos());
    }

    public int getTargetX() { return data.get(0); }
    public int getTargetY() { return data.get(1); }
    public int getTargetZ() { return data.get(2); }
    public int getMode() { return data.get(3); }

    public long getEnergy() {
        long low = data.get(4) & 0xFFFFFFFFL;
        long high = data.get(5) & 0xFFFFFFFFL;
        return (high << 32) | low;
    }

    public boolean isExplosionActive() {
        return data.get(6) != 0;
    }

    public boolean isLightSpearActive() {
        return data.get(7) != 0;
    }

    public HyperCannonControlCenterBlockEntity getBlockEntity() {
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
            // Moving from player inventory to machine slot (chocolate fuel)
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
