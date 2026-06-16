package com.emowolf.scm.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 混合巧克力——由巧克力混合机产出。
 * 营养值、饱和度、成分名称均从 NBT 中动态读取。
 */
public class MixedChocolateItem extends Item {

    public MixedChocolateItem() {
        super(new Properties()
                .stacksTo(64)
                .food(new FoodProperties.Builder()
                        .nutrition(2)
                        .saturationMod(0.2f)
                        .alwaysEat()
                        .fast()
                        .build()));
    }

    /**
     * 从 ItemStack NBT 中读取动态 FoodProperties。
     * 若 NBT 中无营养数据，则回退到基础巧克力属性 (nutrition=2, saturation=0.2)。
     */
    @Override
    public @Nullable FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        if (stack.hasTag()) {
            int nutrition = stack.getTag().getInt("mixed_nutrition");
            float saturation = stack.getTag().getFloat("mixed_saturation");
            return new FoodProperties.Builder()
                    .nutrition(Math.max(nutrition, 1))
                    .saturationMod(Math.max(saturation, 0.1f))
                    .alwaysEat()
                    .fast()
                    .build();
        }
        // 回退默认值
        return new FoodProperties.Builder()
                .nutrition(2)
                .saturationMod(0.2f)
                .alwaysEat()
                .fast()
                .build();
    }

    @Override
    public Component getName(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("ingredients")) {
            String ingredients = stack.getTag().getString("ingredients");
            if (!ingredients.isEmpty()) {
                return Component.translatable("item.chocomaker.mixed_chocolate.named", ingredients);
            }
        }
        return Component.translatable("item.chocomaker.mixed_chocolate");
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && stack.hasTag()) {
            CompoundTag tag = stack.getTag();

            // Apply poison effect
            if (tag.getBoolean("has_poison")) {
                entity.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
            }

            // Apply explosion
            if (tag.getBoolean("has_explosion")) {
                level.explode(null, entity.getX(), entity.getY(), entity.getZ(), 3.0f, Level.ExplosionInteraction.TNT);
            }

            // Apply potion effects
            if (tag.contains("potion_effects")) {
                ListTag effectsList = tag.getList("potion_effects", Tag.TAG_COMPOUND);
                for (int i = 0; i < effectsList.size(); i++) {
                    MobEffectInstance effect = MobEffectInstance.load(effectsList.getCompound(i));
                    if (effect != null) {
                        entity.addEffect(effect);
                    }
                }
            }
        }

        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            int nutrition = tag.getInt("mixed_nutrition");
            float saturation = tag.getFloat("mixed_saturation");
            tooltipComponents.add(Component.translatable("item.chocomaker.mixed_chocolate.nutrition", nutrition));
            tooltipComponents.add(Component.translatable("item.chocomaker.mixed_chocolate.saturation", String.format("%.1f", saturation)));

            if (tag.getBoolean("has_poison")) {
                tooltipComponents.add(Component.translatable("item.chocomaker.mixed_chocolate.poison"));
            }
            if (tag.getBoolean("has_explosion")) {
                tooltipComponents.add(Component.translatable("item.chocomaker.mixed_chocolate.explosion"));
            }
            if (tag.contains("potion_effects")) {
                ListTag effectsList = tag.getList("potion_effects", Tag.TAG_COMPOUND);
                tooltipComponents.add(Component.translatable("item.chocomaker.mixed_chocolate.potion_effects", effectsList.size()));
            }
        }
    }
}
