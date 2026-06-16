package com.emowolf.scm.blockentity;

import com.emowolf.scm.effect.SCMEffects;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class EternalBeaconBlockEntity extends BlockEntity implements MenuProvider {

    public static final int POTION_SLOT = 0;
    public static final int CHOCOLATE_SLOT = 1;
    public static final int RANGE = 16;
    private static final int PERMANENT_DURATION = 999999999; // ~578 days, effectively permanent
    private static final int CONSUME_INTERVAL = 20; // consume every 20 ticks (1 second)

    private long chocolateEnergy = 0;
    private int selectedLevel = 0; // 0~3 -> I~IV

    // Track players currently receiving effects to avoid re-charging energy
    private final Set<UUID> activePlayers = new HashSet<>();

    // ContainerData for syncing to client (long energy encoded as 2 ints)
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> (int) (chocolateEnergy & 0xFFFFFFFF);
                case 1 -> (int) ((chocolateEnergy >>> 32) & 0xFFFFFFFF);
                case 2 -> selectedLevel;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> chocolateEnergy = (chocolateEnergy & 0xFFFFFFFF00000000L) | ((long) value & 0xFFFFFFFFL);
                case 1 -> chocolateEnergy = ((long) value << 32) | (chocolateEnergy & 0xFFFFFFFFL);
                case 2 -> selectedLevel = value;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return switch (slot) {
                case POTION_SLOT -> chocolateEnergy > 0 && stack.getItem() instanceof PotionItem;
                case CHOCOLATE_SLOT -> stack.is(SCMItems.CHOCOLATE.get()) || stack.is(SCMItems.CHOCOLATE_BATTERY.get());
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
            if (maxReceive <= 0) return 0;
            long space = Integer.MAX_VALUE - 1L - chocolateEnergy;
            long received = Math.min(Math.min(maxReceive, space), Integer.MAX_VALUE);
            if (received <= 0) return 0;
            if (!simulate) {
                chocolateEnergy += received;
                setChanged();
            }
            return (int) received;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            long extracted = Math.min(chocolateEnergy, maxExtract);
            if (!simulate) {
                chocolateEnergy -= extracted;
                setChanged();
            }
            return (int) extracted;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(chocolateEnergy, Integer.MAX_VALUE);
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

    public EternalBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(SCMBlockEntities.ETERNAL_BEACON_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chocomaker.eternal_beacon");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return SCMBlockEntities.createBeaconMenu(containerId, playerInventory, this);
    }

    /* ========== NBT ========== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putLong("energy", chocolateEnergy);
        tag.putInt("level", selectedLevel);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        chocolateEnergy = tag.getLong("energy");
        selectedLevel = tag.getInt("level");
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

    public long getChocolateEnergy() {
        return chocolateEnergy;
    }

    public int getSelectedLevel() {
        return selectedLevel;
    }

    public void setSelectedLevel(int level) {
        this.selectedLevel = Math.max(0, Math.min(4, level));
        setChanged();
    }

    /* ========== Tick Logic ========== */

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  EternalBeaconBlockEntity be) {
        // 1) Extract chocolate from chocolate slot into energy (no cap)
        ItemStack chocolateStack = be.inventory.getStackInSlot(CHOCOLATE_SLOT);
        if (!chocolateStack.isEmpty()) {
            int addEnergy = SCMItems.getEnergyValueFE(chocolateStack);
            if (addEnergy > 0) {
                chocolateStack.setCount(0);
                be.chocolateEnergy += addEnergy;
                be.setChanged();
            }
        }

        // 2) Check if there's a potion in the potion slot
        ItemStack potionStack = be.inventory.getStackInSlot(POTION_SLOT);
        if (potionStack.isEmpty() || !(potionStack.getItem() instanceof PotionItem)) {
            be.activePlayers.clear();
            return;
        }

        List<MobEffectInstance> effects = net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(potionStack);
        if (effects.isEmpty()) {
            be.activePlayers.clear();
            return;
        }

        // 3) Continuous energy consumption: cost = (1 + (effectCount + level) ^ (1 + level)) * FE ratio per second
        int effectCount = effects.size();
        int exponent = 1 + be.selectedLevel;
        int costPerSecond = (1 + (int) Math.pow(effectCount + be.selectedLevel, exponent))
                * SCMItems.FE_PER_CHOCOLATE_ENERGY;

        if (level.getGameTime() % CONSUME_INTERVAL == 0) {
            if (be.chocolateEnergy >= costPerSecond) {
                be.chocolateEnergy -= costPerSecond;
                be.setChanged();
            } else {
                // Energy depleted! Trigger catastrophic explosion
                triggerOverloadExplosion(level, pos, be);
                return;
            }
        }

        // 4) Find players in range
        AABB area = new AABB(pos).inflate(RANGE);
        List<Player> playersInRange = level.getEntitiesOfClass(Player.class, area);
        Set<UUID> inRange = new HashSet<>();
        for (Player p : playersInRange) inRange.add(p.getUUID());

        // 5) Remove players who left range from tracking
        be.activePlayers.removeIf(uuid -> !inRange.contains(uuid));

        // 6) Apply permanent effects to players in range (free, as long as machine is running)
        for (Player player : playersInRange) {
            UUID pid = player.getUUID();
            boolean alreadyActive = be.activePlayers.contains(pid);

            if (alreadyActive) {
                MobEffectInstance existing = player.getEffect(effects.get(0).getEffect());
                if (existing == null || existing.getDuration() < PERMANENT_DURATION - 100) {
                    alreadyActive = false;
                }
            }

            if (!alreadyActive) {
                be.activePlayers.add(pid);
            }

            for (MobEffectInstance effect : effects) {
                MobEffectInstance newEffect = new MobEffectInstance(
                        effect.getEffect(),
                        PERMANENT_DURATION,
                        be.selectedLevel,
                        false,
                        false,
                        true
                );
                player.addEffect(newEffect);
            }
        }
    }

    /* ========== Overload Explosion ========== */

    private static void triggerOverloadExplosion(ServerLevel level, BlockPos pos,
                                                  EternalBeaconBlockEntity be) {
        ItemStack potionStack = be.inventory.getStackInSlot(POTION_SLOT);
        List<MobEffectInstance> effects = net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(potionStack);
        int effectCount = Math.max(1, effects.size());
        int exponent = 1 + be.selectedLevel;

        // Explosion power: base 4 + effectCount * exponent, capped at 50
        float explosionPower = Math.min(4.0f + (float) effectCount * exponent, 50.0f);

        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;

        // Sounds
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F, 0.5F);
        level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 2.0F, 0.8F);
        level.playSound(null, cx, cy, cz, SoundEvents.WITHER_HURT, SoundSource.BLOCKS, 2.0F, 0.6F);

        // Massive particle effects
        // Phase 1: Core fireball
        for (int i = 0; i < 120; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double elev = (level.random.nextDouble() - 0.5) * Math.PI;
            double speed = 2.0 + level.random.nextDouble() * 4.0;
            double vx = Math.cos(angle) * Math.cos(elev) * speed;
            double vy = Math.sin(elev) * speed + 1.5;
            double vz = Math.sin(angle) * Math.cos(elev) * speed;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 1, vx * 0.15, vy * 0.15, vz * 0.15, 0.0);
            level.sendParticles(ParticleTypes.FLAME, cx, cy, cz, 1, vx * 0.15, vy * 0.15, vz * 0.15, 0.0);
        }

        // Phase 2: Expanding shockwave rings
        for (int ring = 0; ring < 4; ring++) {
            double ringY = cy + ring * 0.8;
            double ringRadius = 2.0 + ring * 2.0;
            for (int i = 0; i < 60; i++) {
                double angle = 2 * Math.PI * i / 60;
                double x = cx + ringRadius * Math.cos(angle);
                double z = cz + ringRadius * Math.sin(angle);
                level.sendParticles(ParticleTypes.EXPLOSION, x, ringY, z, 1, 0, 0, 0, 0.0);
                level.sendParticles(ParticleTypes.LAVA, x, ringY + 0.1, z, 1, 0, 0.3, 0, 0.0);
            }
        }

        // Phase 3: Spiral energy discharge
        for (int i = 0; i < 80; i++) {
            double t = i / 80.0;
            double angle = t * Math.PI * 8;
            double radius = 0.5 + t * 3.0;
            double y = cy + t * 6.0;
            level.sendParticles(ParticleTypes.END_ROD,
                    cx + radius * Math.cos(angle), y, cz + radius * Math.sin(angle),
                    1, 0, 0.2, 0, 0.0);
        }

        // Actual explosion (destructive, fire = true, guaranteed block destruction)
        Explosion explosion = new Explosion(level, null, cx, cy, cz, explosionPower, true, Explosion.BlockInteraction.DESTROY);
        explosion.explode();
        explosion.finalizeExplosion(true);

        // Drop contents and destroy the beacon
        be.dropContents(level);
        be.activePlayers.clear();
        be.chocolateEnergy = 0L;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        // Notify all players and apply Imaginary Pollution (10 minutes)
        Component message = Component.translatable("msg.chocomaker.eternal_beacon_overload",
                pos.getX(), pos.getY(), pos.getZ(), String.format("%.1f", explosionPower));
        MobEffectInstance pollution = new MobEffectInstance(SCMEffects.IMAGINARY_POLLUTION.get(), 12000, 0, false, false, true);
        for (var sp : level.getServer().getPlayerList().getPlayers()) {
            sp.sendSystemMessage(message);
            // Close GUI for any player currently viewing this beacon
            if (sp.containerMenu instanceof com.emowolf.scm.inventory.EternalBeaconMenu menu
                    && menu.getBlockEntity() == be) {
                sp.closeContainer();
            }
            if (!sp.hasEffect(SCMEffects.IMAGINARY_RESISTANCE.get())) {
                sp.removeAllEffects();
                sp.addEffect(pollution);
            }
        }
    }

    /* ========== Inventory helpers for dropping ========== */

    public void dropContents(ServerLevel level) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }
}
