package com.emowolf.scm.blockentity;

import com.emowolf.scm.item.SCMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ChocolateMixerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int CHOCOLATE_SLOT = 0;
    public static final int FIRST_INGREDIENT_SLOT = 1;
    public static final int LAST_INGREDIENT_SLOT = 8; // slots 1~8 = 8 ingredient slots
    public static final int OUTPUT_SLOT = 9;
    public static final int TOTAL_SLOTS = 10;

    private static final int INGREDIENT_COUNT = LAST_INGREDIENT_SLOT - FIRST_INGREDIENT_SLOT + 1;

    /** Base nutrition contributed by the chocolate itself */
    private static final int BASE_NUTRITION = 2;
    /** Base saturation contributed by the chocolate itself */
    private static final float BASE_SATURATION = 0.2f;
    /** Nutrition added per ingredient that has no food properties */
    private static final float DEFAULT_INGREDIENT_NUTRITION = 0.5f;
    /** Saturation added per ingredient that has no food properties */
    private static final float DEFAULT_INGREDIENT_SATURATION = 0.5f;

    private int mixCooldown = 0;

    /** Cached preview values, recalculated whenever inventory changes */
    private int previewNutrition = BASE_NUTRITION;
    private float previewSaturation = BASE_SATURATION;

    /**
     * ContainerData syncs 3 values to client:
     * [0] = mixing cooldown flag (0: idle, 1: mixing)
     * [1] = preview nutrition (int)
     * [2] = preview saturation * 10 (int, so client renders as /10.0)
     */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> mixCooldown > 0 ? 1 : 0;
                case 1 -> previewNutrition;
                case 2 -> Math.round(previewSaturation * 10);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // no-op, server-driven
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == CHOCOLATE_SLOT) {
                return stack.is(SCMItems.CHOCOLATE.get()) || stack.is(SCMItems.MIXED_CHOCOLATE.get());
            }
            if (slot >= FIRST_INGREDIENT_SLOT && slot <= LAST_INGREDIENT_SLOT) {
                // Accept any item that is not chocolate and not mixed chocolate
                return !stack.is(SCMItems.CHOCOLATE.get()) && !stack.is(SCMItems.MIXED_CHOCOLATE.get());
            }
            // Output slot rejects manual insertion
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            calculatePreview();
        }
    };

    private final LazyOptional<IItemHandler> inventoryCap = LazyOptional.of(() -> inventory);

    public ChocolateMixerBlockEntity(BlockPos pos, BlockState state) {
        super(SCMBlockEntities.CHOCOLATE_MIXER_BE.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.chocomaker.chocolate_mixer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return SCMBlockEntities.createChocolateMixerMenu(containerId, playerInventory, this);
    }

    /* ========== NBT ========== */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putInt("mixCooldown", mixCooldown);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        mixCooldown = tag.getInt("mixCooldown");
        calculatePreview();
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

    public boolean isMixing() {
        return mixCooldown > 0;
    }

    /* ========== Tick Logic ========== */

    public static void serverTick(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state,
                                  ChocolateMixerBlockEntity be) {
        // Handle mix cooldown
        if (be.mixCooldown > 0) {
            be.mixCooldown--;
            be.setChanged();
        }
    }

    /**
     * Called when the player clicks the "Mix" button.
     * Gathers all ingredients and produces a mixed chocolate.
     * @return true if mixing was performed, false if conditions not met
     */
    public boolean tryMix() {
        if (mixCooldown > 0) return false;

        ItemStack chocolateStack = inventory.getStackInSlot(CHOCOLATE_SLOT);
        if (chocolateStack.isEmpty()) return false;

        // Gather ingredients
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = FIRST_INGREDIENT_SLOT; i <= LAST_INGREDIENT_SLOT; i++) {
            ItemStack ing = inventory.getStackInSlot(i);
            if (!ing.isEmpty()) {
                ingredients.add(ing);
            }
        }

        if (ingredients.isEmpty()) return false;

        // Check output slot
        ItemStack outputStack = inventory.getStackInSlot(OUTPUT_SLOT);
        if (!outputStack.isEmpty()) return false;

        // Determine if the base chocolate is mixed chocolate
        boolean isBaseMixedChocolate = chocolateStack.is(SCMItems.MIXED_CHOCOLATE.get());

        // Read old mix_level BEFORE shrink (shrink empties the stack → hasTag() becomes false)
        int oldMixLevel = 0;
        if (isBaseMixedChocolate && chocolateStack.hasTag()) {
            oldMixLevel = chocolateStack.getTag().getInt("mix_level");
        }

        // Start from base values (inherit from mixed chocolate if applicable)
        float totalNutrition = BASE_NUTRITION;
        float totalSaturation = BASE_SATURATION;
        List<String> ingredientNames = new ArrayList<>();

        boolean hasPoison = false;
        boolean hasExplosion = false;
        List<CompoundTag> potionEffects = new ArrayList<>();

        // If base is mixed chocolate, inherit its NBT data
        if (isBaseMixedChocolate && chocolateStack.hasTag()) {
            CompoundTag baseTag = chocolateStack.getTag();
            totalNutrition = baseTag.getInt("mixed_nutrition");
            totalSaturation = baseTag.getFloat("mixed_saturation");
            if (totalNutrition <= 0) totalNutrition = BASE_NUTRITION;
            if (totalSaturation <= 0) totalSaturation = BASE_SATURATION;

            hasPoison = baseTag.getBoolean("has_poison");
            hasExplosion = baseTag.getBoolean("has_explosion");

            if (baseTag.contains("potion_effects")) {
                ListTag baseEffects = baseTag.getList("potion_effects", Tag.TAG_COMPOUND);
                for (int i = 0; i < baseEffects.size(); i++) {
                    potionEffects.add(baseEffects.getCompound(i));
                }
            }

            // Prepend "混合巧克力" to ingredient names
            ingredientNames.add(Component.translatable("item.chocomaker.mixed_chocolate").getString());
        }

        for (ItemStack ingredient : ingredients) {
            String name = ingredient.getHoverName().getString();
            ingredientNames.add(name);

            // Special effects detection
            if (ingredient.is(Items.POISONOUS_POTATO) || ingredient.is(Items.SPIDER_EYE)) {
                hasPoison = true;
            }
            if (ingredient.is(Items.TNT)) {
                hasExplosion = true;
            }

            // Potion effects
            List<MobEffectInstance> effects = PotionUtils.getMobEffects(ingredient);
            for (MobEffectInstance effect : effects) {
                CompoundTag effectTag = new CompoundTag();
                effect.save(effectTag);
                potionEffects.add(effectTag);
            }

            FoodProperties food = ingredient.getItem().getFoodProperties();
            if (food != null) {
                totalNutrition += food.getNutrition();
                totalSaturation += food.getSaturationModifier();
            } else {
                totalNutrition += DEFAULT_INGREDIENT_NUTRITION;
                totalSaturation += DEFAULT_INGREDIENT_SATURATION;
            }
        }

        // Build ingredient names string (Chinese comma separated)
        String ingredientsStr = String.join("、", ingredientNames);

        // Consume one chocolate
        chocolateStack.shrink(1);

        // Consume one from each ingredient slot
        for (int i = FIRST_INGREDIENT_SLOT; i <= LAST_INGREDIENT_SLOT; i++) {
            ItemStack ing = inventory.getStackInSlot(i);
            if (!ing.isEmpty()) {
                ing.shrink(1);
            }
        }

        // Create output
        ItemStack result = new ItemStack(SCMItems.MIXED_CHOCOLATE.get(), 1);
        CompoundTag tag = result.getOrCreateTag();
        tag.putInt("mixed_nutrition", Math.round(totalNutrition));
        tag.putFloat("mixed_saturation", totalSaturation);
        tag.putString("ingredients", ingredientsStr);

        // Always set mix level: first mix = 0, each re-mix inherits +1
        tag.putInt("mix_level", isBaseMixedChocolate ? oldMixLevel + 1 : 0);

        if (hasPoison) tag.putBoolean("has_poison", true);
        if (hasExplosion) tag.putBoolean("has_explosion", true);
        if (!potionEffects.isEmpty()) {
            ListTag list = new ListTag();
            list.addAll(potionEffects);
            tag.put("potion_effects", list);
        }
        inventory.setStackInSlot(OUTPUT_SLOT, result);

        mixCooldown = 5; // small cooldown to prevent instant re-mixing
        setChanged();
        return true;
    }

    /**
     * Calculate expected nutrition and saturation from current slots
     * without consuming any items. Called on inventory change.
     */
    private void calculatePreview() {
        ItemStack chocolateStack = inventory.getStackInSlot(CHOCOLATE_SLOT);
        if (chocolateStack.isEmpty()) {
            previewNutrition = BASE_NUTRITION;
            previewSaturation = BASE_SATURATION;
            return;
        }

        // Start from base values, inherit from mixed chocolate if applicable
        float nutrition = BASE_NUTRITION;
        float saturation = BASE_SATURATION;

        if (chocolateStack.is(SCMItems.MIXED_CHOCOLATE.get()) && chocolateStack.hasTag()) {
            CompoundTag baseTag = chocolateStack.getTag();
            nutrition = baseTag.getInt("mixed_nutrition");
            saturation = baseTag.getFloat("mixed_saturation");
            if (nutrition <= 0) nutrition = BASE_NUTRITION;
            if (saturation <= 0) saturation = BASE_SATURATION;
        }

        for (int i = FIRST_INGREDIENT_SLOT; i <= LAST_INGREDIENT_SLOT; i++) {
            ItemStack ingredient = inventory.getStackInSlot(i);
            if (ingredient.isEmpty()) continue;

            FoodProperties food = ingredient.getItem().getFoodProperties();
            if (food != null) {
                nutrition += food.getNutrition();
                saturation += food.getSaturationModifier();
            } else {
                nutrition += DEFAULT_INGREDIENT_NUTRITION;
                saturation += DEFAULT_INGREDIENT_SATURATION;
            }
        }

        previewNutrition = Math.round(nutrition);
        previewSaturation = saturation;
    }

    /* ========== Drop contents ========== */

    public void dropContents(net.minecraft.server.level.ServerLevel level) {
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
