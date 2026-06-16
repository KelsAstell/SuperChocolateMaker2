package com.emowolf.scm.compat.jei;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 献祭仪式配方数据——纯 JEI 显示用，不参与 Minecraft 配方系统。
 *
 * @param sacrifice  祭品物品（玩家投入物品）
 * @param catalyst   催化剂方块（3×3=9个）
 * @param output     产物物品（null 表示仅为效果解锁，无物品产出）
 * @param description 配方描述（显示在 JEI 中）
 */
public record RitualRecipe(
        Item sacrifice,
        Block catalyst,
        ItemStack output,
        Component description
) {
    /** 催化剂数量 */
    public int catalystCount() {
        return 9;
    }
}
