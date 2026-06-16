package com.emowolf.scm.compat.jei;

import com.emowolf.scm.SCM;
import com.emowolf.scm.block.SCMBlocks;
import com.emowolf.scm.item.SCMItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * JEI 插件：将模组的献祭仪式配方注册到 JEI 中供玩家查阅。
 */
@JeiPlugin
public class SCMJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            new ResourceLocation(SCM.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new RitualRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RitualRecipeCategory.TYPE, createRitualRecipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // 查看祭坛核心时显示所有献祭配方
        registration.addRecipeCatalyst(
                SCMBlocks.ALTAR_CORE_ITEM.get(),
                RitualRecipeCategory.TYPE
        );
    }

    /** 构建所有献祭仪式配方列表 */
    private List<RitualRecipe> createRitualRecipes() {
        return List.of(
                // ── 0) 巧克力献祭 ──
                new RitualRecipe(
                        SCMItems.CHOCOLATE.get(),
                        Blocks.GOLD_BLOCK,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.chocolate.desc")
                ),

                // ── 1) 无限流体 ──
                new RitualRecipe(
                        Items.HEART_OF_THE_SEA,
                        Blocks.DIAMOND_BLOCK,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.infinite_fluid.desc")
                ),

                // ── 2) 飞行仪式 ──
                new RitualRecipe(
                        Items.FEATHER,
                        Blocks.DIAMOND_BLOCK,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.flying.desc")
                ),

                // ── 3) 反怪物Buff ──
                new RitualRecipe(
                        Items.ROTTEN_FLESH,
                        Blocks.IRON_BLOCK,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.anti_mob.desc")
                ),

                // ── 4) 苹果吃法 ──
                new RitualRecipe(
                        Items.CAKE,
                        Blocks.CAKE,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.apple_eat.desc")
                ),

                // ── 5) 无法破坏 ──
                new RitualRecipe(
                        Items.IRON_SWORD,
                        Blocks.IRON_BLOCK,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.unbreakable.desc")
                ),

                // ── 6) 永恒信标 ──
                new RitualRecipe(
                        Items.BEACON,
                        Blocks.EMERALD_BLOCK,
                        new ItemStack(SCMBlocks.ETERNAL_BEACON_ITEM.get()),
                        Component.translatable("jei.chocomaker.ritual.eternal_beacon.desc")
                ),

                // ── 7) 永远的搭档 ──
                new RitualRecipe(
                        Items.NETHER_STAR,
                        Blocks.EMERALD_BLOCK,
                        new ItemStack(SCMItems.TELEPORT_CHARM.get(), 2),
                        Component.translatable("jei.chocomaker.ritual.partner.desc")
                ),

                // ── 8) 本手妙手抄手 ──
                new RitualRecipe(
                        SCMItems.AHOGE.get(),
                        Blocks.EMERALD_BLOCK,
                        new ItemStack(SCMItems.BEN_SHOU_MIAO_SHOU_CHAO_SHOU.get()),
                        Component.translatable("jei.chocomaker.ritual.benshou.desc")
                ),

                // ── 9) 维度护盾 ──
                new RitualRecipe(
                        SCMItems.AHOGE.get(),
                        Blocks.NETHERITE_BLOCK,
                        new ItemStack(SCMItems.DIMENSIONAL_SHIELD.get()),
                        Component.translatable("jei.chocomaker.ritual.dimensional_shield.desc")
                ),

                // ── 10) 飞行护符 ──
                new RitualRecipe(
                        SCMItems.AHOGE.get(),
                        Blocks.DIAMOND_BLOCK,
                        new ItemStack(SCMItems.FLIGHT_CHARM.get()),
                        Component.translatable("jei.chocomaker.ritual.flight_charm.desc")
                ),

                // ── 11) 村民召唤 ──
                new RitualRecipe(
                        Items.EMERALD,
                        Blocks.GRASS_BLOCK,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.villager.desc")
                ),

                // ── 12) 闪电苦力怕 ──
                new RitualRecipe(
                        Items.GUNPOWDER,
                        Blocks.LIGHTNING_ROD,
                        ItemStack.EMPTY,
                        Component.translatable("jei.chocomaker.ritual.charged_creeper.desc")
                ),

                // ── 13) 电池盒 ──
                new RitualRecipe(
                        SCMItems.AHOGE.get(),
                        Blocks.REDSTONE_BLOCK,
                        new ItemStack(SCMItems.BATTERY_BOX.get()),
                        Component.translatable("jei.chocomaker.ritual.battery_box.desc")
                )
        );
    }
}
