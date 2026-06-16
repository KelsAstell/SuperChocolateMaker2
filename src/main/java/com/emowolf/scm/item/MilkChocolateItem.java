package com.emowolf.scm.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

public class MilkChocolateItem extends Item {

    public MilkChocolateItem() {
        super(new Properties()
                .food(new net.minecraft.world.food.FoodProperties.Builder()
                        .nutrition(3)
                        .saturationMod(0.3f)
                        .alwaysEat()
                        .build())
                .stacksTo(64));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            // 清除玩家的所有药水效果
            player.removeAllEffects();
        }
        return result;
    }
}
