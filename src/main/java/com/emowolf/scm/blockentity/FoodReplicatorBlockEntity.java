package com.emowolf.scm.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FoodReplicatorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    /** Base time multiplier: each unit of (nutrition * (1+saturation)) costs this many ticks */
    private static final int BASE_TICKS_PER_UNIT = 20;

    private int progress = 0;
    private int maxProgress = 0;

    /** ContainerData for syncing progress to client (2 ints: progress, maxProgress) */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> progress = value;
                case 1 -> maxProgress = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == INPUT_SLOT) {
                FoodProperties food = stack.getItem().getFoodProperties(stack, null);
                return food != null && food.getSaturationModifier() > 0;
            }
            // Output slot rejects all manual insertion
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == INPUT_SLOT) {
                recalcMaxProgress();
            }
        }
    };

    private final LazyOptional<IItemHandler> inventoryCap = LazyOptional.of(() -> inventory);

    public FoodReplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(SCMBlockEntities.FOOD_REPLICATOR_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chocomaker.food_replicator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return SCMBlockEntities.createFoodReplicatorMenu(containerId, playerInventory, this);
    }

    /* ========== NBT ========== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        progress = tag.getInt("progress");
        maxProgress = tag.getInt("maxProgress");
    }

    /* ========== Capability ========== */

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryCap.invalidate();
    }

    /* ========== Accessors ========== */

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public int getProgress() {
        return progress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    /* ========== Helpers ========== */

    /**
     * Calculate the max progress based on the food in the input slot.
     * Formula: nutrition * (1 + saturationMod) * BASE_TICKS_PER_UNIT
     */
    private void recalcMaxProgress() {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            maxProgress = 0;
            progress = 0;
            return;
        }
        FoodProperties food = input.getItem().getFoodProperties(input, null);
        if (food == null || food.getSaturationModifier() <= 0) {
            maxProgress = 0;
            progress = 0;
            return;
        }
        maxProgress = (int) (food.getNutrition() * (1.0 + food.getSaturationModifier()) * BASE_TICKS_PER_UNIT);
        // Clamp progress if it exceeds new max
        if (progress > maxProgress) {
            progress = maxProgress;
        }
    }

    /**
     * Check if the output slot can accept one more copy of the input item.
     */
    private boolean canOutput() {
        ItemStack input = inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) return false;
        ItemStack output = inventory.getStackInSlot(OUTPUT_SLOT);
        if (output.isEmpty()) return true;
        return ItemStack.isSameItem(input, output) && output.getCount() < output.getMaxStackSize();
    }

    /* ========== Tick Logic ========== */

    public static void serverTick(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state,
                                  FoodReplicatorBlockEntity be) {
        ItemStack input = be.inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty()) {
            if (be.progress > 0) {
                be.progress = 0;
                be.setChanged();
            }
            return;
        }

        FoodProperties food = input.getItem().getFoodProperties(input, null);
        if (food == null || food.getSaturationModifier() <= 0) {
            if (be.progress > 0) {
                be.progress = 0;
                be.setChanged();
            }
            return;
        }

        // Recalculate max progress in case input changed
        int targetMax = (int) (food.getNutrition() * (1.0 + food.getSaturationModifier()) * BASE_TICKS_PER_UNIT);
        if (be.maxProgress != targetMax) {
            be.maxProgress = targetMax;
            if (be.progress > be.maxProgress) be.progress = be.maxProgress;
        }

        // Check output slot
        if (!be.canOutput()) {
            return;
        }

        // Increase progress
        be.progress++;
        be.setChanged();

        // Complete replication
        if (be.progress >= be.maxProgress) {
            be.progress = 0;
            ItemStack output = be.inventory.getStackInSlot(OUTPUT_SLOT);
            ItemStack copy = input.copy();
            copy.setCount(1);
            if (output.isEmpty()) {
                be.inventory.setStackInSlot(OUTPUT_SLOT, copy);
            } else {
                output.grow(1);
            }
            be.setChanged();
        }
    }

    /* ========== Drop contents ========== */

    public void dropContents(net.minecraft.server.level.ServerLevel level) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
