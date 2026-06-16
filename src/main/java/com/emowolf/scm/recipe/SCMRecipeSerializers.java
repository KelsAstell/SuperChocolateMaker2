package com.emowolf.scm.recipe;

import com.emowolf.scm.SCM;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SCMRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, SCM.MODID);

    public static final RegistryObject<RecipeSerializer<BatteryBoxStyleRecipe>> BATTERY_BOX_STYLE =
            RECIPE_SERIALIZERS.register("crafting_special_battery_box_style",
                    () -> new SimpleCraftingRecipeSerializer<>(BatteryBoxStyleRecipe::new));
}
