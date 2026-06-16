package com.emowolf.scm.blockentity;

import com.emowolf.scm.item.SCMItems;
import com.emowolf.scm.network.SCMNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class HyperCannonControlCenterBlockEntity extends BlockEntity implements MenuProvider {

    public static final int CHOCOLATE_SLOT = 0;

    // Firing target coordinates
    private int targetX = 0;
    private int targetY = 64;
    private int targetZ = 0;
    private int mode = 0; // 0=Arrow, 1=Light Spear, 2=Explosion

    private long energy = 0;

    // Explosion state machine
    private boolean explosionActive = false;
    private int explosionY = 320;
    private static final int EXPLOSION_START_Y = 320;
    private static final int EXPLOSION_END_Y = -64;
    private static final int EXPLOSION_STEP = 5;
    private static final int EXPLOSION_INTERVAL = 10; // ticks between explosions

    // Light Spear state machine
    private boolean lightSpearActive = false;
    private int lightSpearTimer = 0;
    @Nullable private UUID lightSpearPlayerUUID = null;
    private static final int LIGHT_SPEAR_DELAY = 40; // 2 seconds beam before kill

    // ContainerData for syncing to client
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> targetX;
                case 1 -> targetY;
                case 2 -> targetZ;
                case 3 -> mode;
                case 4 -> (int) (energy & 0xFFFFFFFF);
                case 5 -> (int) ((energy >>> 32) & 0xFFFFFFFF);
                case 6 -> explosionActive ? 1 : 0;
                case 7 -> lightSpearActive ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> targetX = value;
                case 1 -> targetY = value;
                case 2 -> targetZ = value;
                case 3 -> mode = value;
                case 4 -> energy = (energy & 0xFFFFFFFF00000000L) | (value & 0xFFFFFFFFL);
                case 5 -> energy = (energy & 0xFFFFFFFFL) | ((long) value << 32);
            }
        }

        @Override
        public int getCount() {
            return 8;
        }
    };

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.is(SCMItems.CHOCOLATE.get()) || stack.is(SCMItems.CHOCOLATE_BATTERY.get());
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
            if (maxReceive <= 0) return 0;
            long space = Integer.MAX_VALUE - 1L - energy;
            long received = Math.min(Math.min(maxReceive, space), Integer.MAX_VALUE);
            if (received <= 0) return 0;
            if (!simulate) {
                energy += received;
                setChanged();
            }
            return (int) received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            long extracted = Math.min(energy, maxExtract);
            if (!simulate) {
                energy -= extracted;
                setChanged();
            }
            return (int) extracted;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(energy, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE - 1;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyStorage);

    public HyperCannonControlCenterBlockEntity(BlockPos pos, BlockState state) {
        super(SCMBlockEntities.HYPER_CANNON_CONTROL_CENTER_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chocomaker.hyper_cannon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return SCMBlockEntities.createHyperCannonMenu(containerId, playerInventory, this);
    }

    /* ========== NBT ========== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putInt("targetX", targetX);
        tag.putInt("targetY", targetY);
        tag.putInt("targetZ", targetZ);
        tag.putInt("mode", mode);
        tag.putLong("energy", energy);
        tag.putBoolean("explosionActive", explosionActive);
        tag.putInt("explosionY", explosionY);
        tag.putBoolean("lightSpearActive", lightSpearActive);
        tag.putInt("lightSpearTimer", lightSpearTimer);
        if (lightSpearPlayerUUID != null) {
            tag.putUUID("lightSpearPlayer", lightSpearPlayerUUID);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        targetX = tag.getInt("targetX");
        targetY = tag.getInt("targetY");
        targetZ = tag.getInt("targetZ");
        mode = tag.getInt("mode");
        energy = tag.getLong("energy");
        explosionActive = tag.getBoolean("explosionActive");
        explosionY = tag.getInt("explosionY");
        lightSpearActive = tag.getBoolean("lightSpearActive");
        lightSpearTimer = tag.getInt("lightSpearTimer");
        if (tag.hasUUID("lightSpearPlayer")) {
            lightSpearPlayerUUID = tag.getUUID("lightSpearPlayer");
        }
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

    public long getEnergy() {
        return energy;
    }

    public int getTargetX() { return targetX; }
    public int getTargetY() { return targetY; }
    public int getTargetZ() { return targetZ; }
    public int getMode() { return mode; }

    public void setTargetX(int v) { targetX = v; setChanged(); }
    public void setTargetY(int v) { targetY = v; setChanged(); }
    public void setTargetZ(int v) { targetZ = v; setChanged(); }
    public void setMode(int v) { mode = Math.max(0, Math.min(2, v)); setChanged(); }

    public boolean isExplosionActive() { return explosionActive; }
    public boolean isLightSpearActive() { return lightSpearActive; }
    public boolean isFiring() { return explosionActive || lightSpearActive; }

    /* ========== Energy Cost ========== */

    public static int calculateCost(int blockX, int blockZ, int targetX, int targetZ, int mode) {
        double dx = targetX - blockX;
        double dz = targetZ - blockZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double cost = switch (mode) {
            case 0 -> distance * 10.0;  // Arrow
            case 1 -> distance * 15.0;  // Light Spear
            case 2 -> distance * 25.0;  // Explosion
            default -> 0;
        };
        return (int) Math.ceil(Math.max(1, cost)) * SCMItems.FE_PER_CHOCOLATE_ENERGY;
    }

    private static Component getModeComponent(int mode) {
        String key = switch (mode) {
            case 0 -> "gui.chocomaker.hyper_cannon.mode.arrow";
            case 1 -> "gui.chocomaker.hyper_cannon.mode.light_spear";
            case 2 -> "gui.chocomaker.hyper_cannon.mode.explosion";
            default -> "gui.chocomaker.hyper_cannon.mode.arrow";
        };
        return Component.translatable(key);
    }

    /* ========== Tick Logic ========== */

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  HyperCannonControlCenterBlockEntity be) {
        // 1) Extract chocolate from fuel slot into energy
        ItemStack stack = be.inventory.getStackInSlot(CHOCOLATE_SLOT);
        if (!stack.isEmpty()) {
            int addEnergy = SCMItems.getEnergyValueFE(stack);
            if (addEnergy > 0) {
                stack.setCount(0);
                be.energy += addEnergy;
                be.setChanged();
            }
        }

        // 2) Explosion state machine
        if (be.explosionActive) {
            if (level.getGameTime() % EXPLOSION_INTERVAL == 0) {
                if (be.explosionY >= EXPLOSION_END_Y) {
                    double cx = be.targetX + 0.5;
                    double cz = be.targetZ + 0.5;
                    level.explode(null, cx, be.explosionY, cz, 4.0f,
                            Level.ExplosionInteraction.NONE);
                    level.playSound(null, cx, be.explosionY, cz,
                            SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5f, 1.0f);
                    be.explosionY -= EXPLOSION_STEP;
                } else {
                    be.explosionActive = false;
                    be.setChanged();
                }
            }
        }

        // 3) Light Spear state machine
        if (be.lightSpearActive) {
            be.lightSpearTimer--;

            // Play warning sound halfway through
            if (be.lightSpearTimer == LIGHT_SPEAR_DELAY / 2) {
                level.playSound(null, be.targetX + 0.5, be.targetY, be.targetZ + 0.5,
                        SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0f, 0.5f);
            }

            // Timer expired: execute the kill
            if (be.lightSpearTimer <= 0) {
                executeLightSpearKill(level, be);
                be.lightSpearActive = false;
                be.lightSpearPlayerUUID = null;
                be.setChanged();
            }
        }
    }

    /* ========== Fire Logic ========== */

    /**
     * Fire the cannon to the configured target coordinates using the configured mode.
     * Called from network packet handler or redstone trigger.
     */
    public boolean fire(ServerLevel level, Player player) {
        if (explosionActive || lightSpearActive) return false; // already firing

        int cost = calculateCost(worldPosition.getX(), worldPosition.getZ(), targetX, targetZ, mode);
        if (energy < cost) {
            player.sendSystemMessage(Component.translatable("msg.chocomaker.hyper_cannon.no_energy", cost, energy)
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        energy -= cost;
        setChanged();

        switch (mode) {
            case 0 -> fireArrowMode(level, player);
            case 1 -> fireLightSpearMode(level, player);
            case 2 -> fireExplosionMode(level, player);
        }

        // Sound effect
        double bx = worldPosition.getX() + 0.5;
        double by = worldPosition.getY() + 0.5;
        double bz = worldPosition.getZ() + 0.5;
        level.playSound(null, bx, by, bz, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0f, 0.5f);
        level.playSound(null, bx, by, bz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.5f, 1.2f);

        player.sendSystemMessage(Component.translatable("msg.chocomaker.hyper_cannon.fire",
                targetX, targetY, targetZ, getModeComponent(mode), cost)
                .withStyle(ChatFormatting.AQUA));

        return true;
    }

    /** Arrow mode: spawn high-velocity arrows from the sky */
    private void fireArrowMode(ServerLevel level, Player player) {
        double spawnY = 320.0;
        double centerX = targetX + 0.5;
        double centerZ = targetZ + 0.5;

        // Spawn a spread of arrows for a devastating kinetic bombardment
        int arrowCount = 8;
        for (int i = 0; i < arrowCount; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 3.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 3.0;

            Arrow arrow = new Arrow(level, centerX + offsetX, spawnY, centerZ + offsetZ);
            arrow.setOwner(player);
            arrow.setDeltaMovement(0, -3.0, 0); // extreme downward velocity
            arrow.setCritArrow(true);
            arrow.setBaseDamage(20.0); // 10 hearts base damage
            arrow.pickup = Arrow.Pickup.DISALLOWED;
            level.addFreshEntity(arrow);
        }

        level.playSound(null, centerX, spawnY, centerZ,
                SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 2.0f, 0.8f);
    }

    /** Light Spear mode: show red beacon beam for 2 seconds, then kill all entities in chunk */
    private void fireLightSpearMode(ServerLevel level, Player player) {
        lightSpearActive = true;
        lightSpearTimer = LIGHT_SPEAR_DELAY;
        lightSpearPlayerUUID = player.getUUID();
        setChanged();

        player.sendSystemMessage(Component.translatable("msg.chocomaker.hyper_cannon.light_spear_charging",
                targetX, targetZ)
                .withStyle(ChatFormatting.RED));

        double cx = targetX + 0.5;
        double cz = targetZ + 0.5;
        level.playSound(null, cx, targetY, cz,
                SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0f, 0.3f);

        // 向所有客户端广播红色信标光束特效
        SCMNetwork.LightSpearBeamPacket.sendToAll(targetX, targetZ, LIGHT_SPEAR_DELAY);
    }

    /** Execute the light spear kill on all non-player living entities in the target chunk */
    private static void executeLightSpearKill(ServerLevel level, HyperCannonControlCenterBlockEntity be) {
        ChunkPos chunkPos = new ChunkPos(new BlockPos(be.targetX, be.targetY, be.targetZ));
        double minX = chunkPos.getMinBlockX();
        double minZ = chunkPos.getMinBlockZ();
        double maxX = chunkPos.getMaxBlockX() + 1;
        double maxZ = chunkPos.getMaxBlockZ() + 1;
        AABB chunkArea = new AABB(minX, level.getMinBuildHeight(), minZ,
                maxX, level.getMaxBuildHeight(), maxZ);

        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, chunkArea,
                e -> !(e instanceof Player));

        Player player = null;
        if (be.lightSpearPlayerUUID != null) {
            player = level.getPlayerByUUID(be.lightSpearPlayerUUID);
        }

        int killed = 0;
        for (LivingEntity entity : entities) {
            if (player != null) {
                entity.setLastHurtByPlayer(player);
                entity.setLastHurtByMob(player);
                entity.hurt(level.damageSources().playerAttack(player), Float.MAX_VALUE);
            } else {
                entity.hurt(level.damageSources().magic(), Float.MAX_VALUE);
            }
            if (entity.isAlive()) {
                entity.kill();
            }
            killed++;
        }

        if (player != null) {
            player.sendSystemMessage(Component.translatable("msg.chocomaker.hyper_cannon.light_spear_killed", killed)
                    .withStyle(ChatFormatting.AQUA));
        }

        double cx = be.targetX + 0.5;
        double cz = be.targetZ + 0.5;
        level.playSound(null, cx, be.targetY, cz,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 2.0f, 1.0f);
    }

    /** Explosion mode: start cascading explosions from top to bottom */
    private void fireExplosionMode(ServerLevel level, Player player) {
        explosionY = EXPLOSION_START_Y;
        explosionActive = true;
        setChanged();

        player.sendSystemMessage(Component.translatable("msg.chocomaker.hyper_cannon.explosion_start",
                targetX, targetZ)
                .withStyle(ChatFormatting.RED));
    }

    /* ========== Drop contents ========== */

    public void dropContents(ServerLevel level) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level,
                        worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
