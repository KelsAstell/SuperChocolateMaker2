package com.emowolf.scm.recipe;

import com.emowolf.scm.item.BatteryBoxItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 电池盒纹饰切换配方：工作台中单独放入电池盒，即可在两种纹饰间切换。
 * 保留所有 NBT 数据（巧克力存量、电池存量、FE 累加器），仅翻转 Style tag。
 */
public class BatteryBoxStyleRecipe extends CustomRecipe {

    public BatteryBoxStyleRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        int batteryBoxCount = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                if (stack.is(SCMItems.BATTERY_BOX.get())) {
                    batteryBoxCount++;
                } else {
                    return false; // 有其他物品 → 不匹配
                }
            }
        }
        return batteryBoxCount == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        ItemStack input = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(SCMItems.BATTERY_BOX.get())) {
                input = stack;
                break;
            }
        }
        if (input.isEmpty()) return ItemStack.EMPTY;

        // 复制输入物品并切换纹饰风格（NBT 完整保留）
        ItemStack result = input.copy();
        BatteryBoxItem.toggleStyle(result);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SCMRecipeSerializers.BATTERY_BOX_STYLE.get();
    }
}
