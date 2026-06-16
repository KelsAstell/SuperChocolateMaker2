package com.emowolf.scm.item;

import com.emowolf.scm.effect.SCMEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class EnchantedGoldenChocolateItem extends Item {

    public EnchantedGoldenChocolateItem() {
        super(new Properties()
                .food(new FoodProperties.Builder()
                        .nutrition(4)
                        .saturationMod(1.2f)
                        .alwaysEat()
                        .fast()
                        .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 400, 1), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 6000, 0), 1.0f)
                        .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 6000, 0), 1.0f)
                        .effect(() -> new MobEffectInstance(SCMEffects.IMAGINARY_RESISTANCE.get(), 6000, 0), 1.0f)
                        .build())
                .stacksTo(64)
                .rarity(Rarity.EPIC));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("item.chocomaker.enchanted_golden_chocolate.desc"));
    }
}
