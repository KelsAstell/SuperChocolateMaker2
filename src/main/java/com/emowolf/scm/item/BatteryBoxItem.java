package com.emowolf.scm.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.energy.IEnergyStorage;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

public class BatteryBoxItem extends Item implements ICurioItem {

    /** 效率值（FE/t），吸收巧克力 +1.0，吸收巧克力电池 +2.5 */
    public static final String TAG_EFFICIENCY = "Efficiency";
    /** 分数 FE 累加器（浮点 FE 产出按 tick 累积，达到整数后对外输出） */
    public static final String TAG_FE_ACCUMULATOR = "FeAccumulator";
    /** 纹饰风格：0=恶魔狼狼艾斯纹饰, 1=瓜子纹饰 */
    public static final String TAG_STYLE = "Style";
    public static final int STYLE_DEMON_WOLF = 0;
    public static final int STYLE_SEED = 1;

    // FE 产出系数（吸收时用于累加效率）
    private static final double FE_PER_CHOCOLATE = 1.0;
    private static final double FE_PER_BATTERY = 2.5;

    public BatteryBoxItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        // 只允许装备在belt栏位
        return slotContext.identifier().equals("belt");
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
    }

    /**
     * 批量吸收巧克力或巧克力电池（整组一次性吸收，类似 DimensionalShieldItem 的 increaseRegenRate 模式）。
     * @param batteryBox  电池盒 ItemStack
     * @param itemToAbsorb 待吸收的物品堆叠
     * @return 实际吸收的数量
     */
    public static int absorbItem(ItemStack batteryBox, ItemStack itemToAbsorb) {
        if (batteryBox.getItem() != SCMItems.BATTERY_BOX.get()) return 0;

        CompoundTag tag = batteryBox.getOrCreateTag();
        int want = itemToAbsorb.getCount();
        double current = tag.getDouble(TAG_EFFICIENCY);

        if (itemToAbsorb.is(SCMItems.CHOCOLATE.get())) {
            tag.putDouble(TAG_EFFICIENCY, current + want * FE_PER_CHOCOLATE);
            itemToAbsorb.shrink(want);
            return want;
        } else if (itemToAbsorb.is(SCMItems.CHOCOLATE_BATTERY.get())) {
            tag.putDouble(TAG_EFFICIENCY, current + want * FE_PER_BATTERY);
            itemToAbsorb.shrink(want);
            return want;
        }

        return 0;
    }

    /**
     * 获取当前 FE 产出率（直接返回效率值）。
     */
    public static double getFEOutput(ItemStack batteryBox) {
        if (batteryBox.getItem() != SCMItems.BATTERY_BOX.get()) return 0.0;
        return batteryBox.getOrCreateTag().getDouble(TAG_EFFICIENCY);
    }

    // ========== 静态辅助方法 ==========

    /** 获取效率值 */
    public static double getEfficiency(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return 0.0;
        return stack.getOrCreateTag().getDouble(TAG_EFFICIENCY);
    }

    /** 设置效率值 */
    public static void setEfficiency(ItemStack stack, double value) {
        stack.getOrCreateTag().putDouble(TAG_EFFICIENCY, value);
    }

    /** 获取分数 FE 累加器值 */
    public static double getFeAccumulator(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return 0.0;
        return stack.getOrCreateTag().getDouble(TAG_FE_ACCUMULATOR);
    }

    /** 设置分数 FE 累加器值 */
    public static void setFeAccumulator(ItemStack stack, double value) {
        stack.getOrCreateTag().putDouble(TAG_FE_ACCUMULATOR, value);
    }

    // ========== 纹饰风格 ==========

    /** 获取当前纹饰风格 */
    public static int getStyle(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return STYLE_DEMON_WOLF;
        return stack.getOrCreateTag().getInt(TAG_STYLE);
    }

    /** 设置纹饰风格 */
    public static void setStyle(ItemStack stack, int style) {
        stack.getOrCreateTag().putInt(TAG_STYLE, style);
    }

    /** 切换纹饰风格并返回新风格值 */
    public static int toggleStyle(ItemStack stack) {
        int current = getStyle(stack);
        int next = (current == STYLE_DEMON_WOLF) ? STYLE_SEED : STYLE_DEMON_WOLF;
        setStyle(stack, next);
        return next;
    }

    // ========== FE 能量接口 ==========

    /**
     * 创建一个能量存储实现，用于对外输出 FE
     */
    public static IEnergyStorage createEnergyStorage(ItemStack batteryBox) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return 0;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return (int) Math.min(getFEOutput(batteryBox), maxExtract);
            }

            @Override
            public int getEnergyStored() {
                return (int) getFEOutput(batteryBox);
            }

            @Override
            public int getMaxEnergyStored() {
                return Integer.MAX_VALUE;
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return false;
            }
        };
    }

    // ========== Tooltip ==========

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);

        int style = getStyle(stack);
        String styleName = style == STYLE_DEMON_WOLF ? "恶魔狼狼艾斯纹饰" : "瓜子纹饰";
        double efficiency = getEfficiency(stack);
        double feOutput = getFEOutput(stack);

        tooltipComponents.add(Component.translatable("item.chocomaker.battery_box.desc"));
        tooltipComponents.add(Component.literal(styleName).withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("item.chocomaker.battery_box.efficiency", DimensionalShieldItem.formatCompact(efficiency)));
        tooltipComponents.add(Component.translatable("item.chocomaker.battery_box.output", DimensionalShieldItem.formatCompact(feOutput)));
    }
}