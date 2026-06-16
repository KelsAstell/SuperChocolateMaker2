package com.emowolf.scm.blockentity;

import com.emowolf.scm.item.SCMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChocolateAnnihilationGeneratorBlockEntity extends BlockEntity implements MenuProvider {

    public static final int CHOCOLATE_SLOT = 0;
    public static final int CHARGE_SLOT = 1;
    public static final long MAX_ENERGY = 1_000_000_000L; // 1G FE
    private static final int DECAY_INTERVAL = 20; // every second
    private static final int CHARGE_RATE = 100000; // max FE/t to charge items

    private long storedEnergy = 0;
    private double generationRate = 0.0;

    // ContainerData for syncing to client
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) (storedEnergy & 0xFFFFFFFF);
                case 1 -> (int) ((storedEnergy >>> 32) & 0xFFFFFFFF);
                case 2 -> (int) Double.doubleToRawLongBits(generationRate);
                case 3 -> (int) (Double.doubleToRawLongBits(generationRate) >>> 32);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Client receives synced data from server, must store it so get() returns correct values
            switch (index) {
                case 0 -> storedEnergy = (storedEnergy & 0xFFFFFFFF00000000L) | ((long) value & 0xFFFFFFFFL);
                case 1 -> storedEnergy = ((long) value << 32) | (storedEnergy & 0xFFFFFFFFL);
                case 2 -> {
                    long bits = Double.doubleToRawLongBits(generationRate);
                    generationRate = Double.longBitsToDouble((bits & 0xFFFFFFFF00000000L) | ((long) value & 0xFFFFFFFFL));
                }
                case 3 -> {
                    long bits = Double.doubleToRawLongBits(generationRate);
                    generationRate = Double.longBitsToDouble(((long) value << 32) | (bits & 0xFFFFFFFFL));
                }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case CHOCOLATE_SLOT -> stack.is(SCMItems.CHOCOLATE.get()) || stack.is(SCMItems.CHOCOLATE_BATTERY.get());
                case CHARGE_SLOT -> stack.getCapability(ForgeCapabilities.ENERGY).map(storage -> storage.canReceive()).orElse(false);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> inventoryCap = LazyOptional.of(() -> inventory);

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0; // Generator, cannot receive
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            long extracted = Math.min(storedEnergy, maxExtract);
            if (!simulate) {
                storedEnergy -= extracted;
                setChanged();
            }
            return (int) extracted;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(storedEnergy, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    };

    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyStorage);

    public ChocolateAnnihilationGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(SCMBlockEntities.CHOCOLATE_ANNIHILATION_GENERATOR_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chocomaker.chocolate_annihilation_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return SCMBlockEntities.createGeneratorMenu(containerId, playerInventory, this);
    }

    /* ========== NBT ========== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putLong("storedEnergy", storedEnergy);
        tag.putDouble("generationRate", generationRate);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        storedEnergy = tag.getLong("storedEnergy");
        generationRate = tag.getDouble("generationRate");
    }

    /* ========== Capability ========== */

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return inventoryCap.cast();
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryCap.invalidate();
        energyCap.invalidate();
    }

    /* ========== Accessors ========== */

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public long getStoredEnergy() {
        return storedEnergy;
    }

    public double getGenerationRate() {
        return generationRate;
    }

    /* ========== Tick Logic ========== */

    public static void serverTick(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state,
                                  ChocolateAnnihilationGeneratorBlockEntity be) {
        // 1) Consume chocolate / chocolate battery from slot, add to generation rate
        ItemStack stack = be.inventory.getStackInSlot(CHOCOLATE_SLOT);
        if (!stack.isEmpty()) {
            double addRate = 0;
            if (stack.is(SCMItems.CHOCOLATE_BATTERY.get())) {
                addRate = 3.0 * stack.getCount();
            } else if (stack.is(SCMItems.CHOCOLATE.get())) {
                addRate = 1.0 * stack.getCount();
            }
            if (addRate > 0) {
                stack.setCount(0);
                be.generationRate = Math.min(be.generationRate + addRate, Double.MAX_VALUE - 1);
                be.setChanged();
            }
        }

        // 2) Decay: generation rate drops by 1 FE/t every second
        if (level.getGameTime() % DECAY_INTERVAL == 0 && be.generationRate > 0) {
            be.generationRate = Math.max(0.0, be.generationRate - 1.0);
            be.setChanged();
        }

        // 3) Produce energy: generationRate FE per tick
        if (be.generationRate > 0 && be.storedEnergy < MAX_ENERGY) {
            long toAdd = (long) Math.min(be.generationRate, Integer.MAX_VALUE);
            be.storedEnergy = Math.min(be.storedEnergy + toAdd, MAX_ENERGY);
            be.setChanged();
        }

        // 4) Push energy to adjacent blocks that can receive energy
        if (be.storedEnergy > 0) {
            for (Direction dir : Direction.values()) {
                if (be.storedEnergy <= 0) break;
                BlockEntity neighbor = level.getBlockEntity(pos.relative(dir));
                if (neighbor != null) {
                    neighbor.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).ifPresent(receiver -> {
                        if (receiver.canReceive()) {
                            int toSend = (int) Math.min(be.storedEnergy, Integer.MAX_VALUE);
                            int accepted = receiver.receiveEnergy(toSend, false);
                            if (accepted > 0) {
                                be.storedEnergy -= accepted;
                                be.setChanged();
                            }
                        }
                    });
                }
            }
        }

        // 5) Charge item in charge slot from stored energy
        if (be.storedEnergy > 0) {
            ItemStack chargeStack = be.inventory.getStackInSlot(CHARGE_SLOT);
            if (!chargeStack.isEmpty()) {
                chargeStack.getCapability(ForgeCapabilities.ENERGY).ifPresent(receiver -> {
                    if (receiver.canReceive()) {
                        int toSend = (int) Math.min(be.storedEnergy, CHARGE_RATE);
                        int accepted = receiver.receiveEnergy(toSend, false);
                        if (accepted > 0) {
                            be.storedEnergy -= accepted;
                            be.setChanged();
                        }
                    }
                });
            }
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
