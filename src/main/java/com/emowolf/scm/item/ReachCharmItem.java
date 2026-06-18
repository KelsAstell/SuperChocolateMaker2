package com.emowolf.scm.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class ReachCharmItem extends Item implements ICurioItem {

    private static final UUID ENTITY_REACH_UUID = UUID.fromString("c8b9e5a1-3f5a-4d2c-9e8f-1a7b3c5d8f2e");
    private static final UUID BLOCK_REACH_UUID = UUID.fromString("d9a0f6b2-4a6b-5e3d-8f9c-2b8c4d6e9a3f");
    private static final String REACH_MODIFIER_NAME = "ReachCharmRange";

    /** NBT标签：已吸收金巧克力累积的额外加成 */
    public static final String TAG_BONUS = "ReachBonus";

    /** 基础攻击距离加成 */
    private static final double BASE_BONUS = 0.1;
    /** 每个金巧克力提供的额外加成 */
    private static final double GOLDEN_CHOCOLATE_BONUS = 0.1;
    /** 总加成上限 */
    private static final double MAX_BONUS = 6.0;

    public ReachCharmItem(Properties properties) {
        super(properties);
    }

    // ========== Curios 接口 ==========

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        applyModifier(slotContext.entity(), getTotalBonus(stack));
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        removeModifier(slotContext.entity());
    }

    // ========== 属性修饰符管理 ==========

    private static void applyModifier(net.minecraft.world.entity.Entity entity, double value) {
        if (entity instanceof LivingEntity living) {
            // 实体交互距离（攻击实体）
            AttributeInstance entityReach = living.getAttribute(ForgeMod.ENTITY_REACH.get());
            if (entityReach != null && entityReach.getModifier(ENTITY_REACH_UUID) == null) {
                entityReach.addPermanentModifier(new AttributeModifier(
                        ENTITY_REACH_UUID, REACH_MODIFIER_NAME,
                        value, AttributeModifier.Operation.ADDITION));
            }
            // 方块/容器交互距离（挖掘方块、打开箱子等）
            AttributeInstance blockReach = living.getAttribute(ForgeMod.BLOCK_REACH.get());
            if (blockReach != null && blockReach.getModifier(BLOCK_REACH_UUID) == null) {
                blockReach.addPermanentModifier(new AttributeModifier(
                        BLOCK_REACH_UUID, REACH_MODIFIER_NAME,
                        value, AttributeModifier.Operation.ADDITION));
            }
        }
    }

    private static void removeModifier(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof LivingEntity living) {
            AttributeInstance entityReach = living.getAttribute(ForgeMod.ENTITY_REACH.get());
            if (entityReach != null) {
                entityReach.removeModifier(ENTITY_REACH_UUID);
            }
            AttributeInstance blockReach = living.getAttribute(ForgeMod.BLOCK_REACH.get());
            if (blockReach != null) {
                blockReach.removeModifier(BLOCK_REACH_UUID);
            }
        }
    }

    // ========== 加成计算与吸收 ==========

    /**
     * 获取当前总攻击距离加成（基础 + 已吸收累积）。
     */
    public static double getTotalBonus(ItemStack stack) {
        if (!stack.is(SCMItems.REACH_CHARM.get())) return 0.0;
        return Math.min(BASE_BONUS + stack.getOrCreateTag().getDouble(TAG_BONUS), MAX_BONUS);
    }

    /**
     * 获取已吸收金巧克力累积的额外加成（不含基础值）。
     */
    public static double getAbsorbedBonus(ItemStack stack) {
        if (!stack.is(SCMItems.REACH_CHARM.get())) return 0.0;
        return stack.getOrCreateTag().getDouble(TAG_BONUS);
    }

    /**
     * 吸收金巧克力，增加攻击距离加成。总加成上限为 {@value #MAX_BONUS}。
     * @param charm    护符 ItemStack
     * @param goldenChocolate 金巧克力 ItemStack
     * @return 实际吸收的数量
     */
    public static int absorbGoldenChocolate(ItemStack charm, ItemStack goldenChocolate) {
        if (!charm.is(SCMItems.REACH_CHARM.get())) return 0;
        if (!goldenChocolate.is(SCMItems.GOLDEN_CHOCOLATE.get())) return 0;

        int count = goldenChocolate.getCount();
        CompoundTag tag = charm.getOrCreateTag();
        double currentBonus = tag.getDouble(TAG_BONUS);
        double nextBonus = currentBonus + count * GOLDEN_CHOCOLATE_BONUS;
        if (nextBonus > MAX_BONUS - BASE_BONUS) {
            // 计算实际可吸收的数量
            int maxAbsorbCount = (int)((MAX_BONUS - BASE_BONUS - currentBonus) / GOLDEN_CHOCOLATE_BONUS);
            if (maxAbsorbCount <= 0) return 0;
            count = Math.min(count, maxAbsorbCount);
            nextBonus = currentBonus + count * GOLDEN_CHOCOLATE_BONUS;
        }
        tag.putDouble(TAG_BONUS, nextBonus);
        goldenChocolate.shrink(count);
        return count;
    }

    // ========== Tooltip ==========

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        double total = getTotalBonus(stack);
        tooltipComponents.add(Component.translatable("item.chocomaker.reach_charm.desc"));
        tooltipComponents.add(Component.translatable("item.chocomaker.reach_charm.bonus",
                String.format("%.1f", total)));
    }
}
