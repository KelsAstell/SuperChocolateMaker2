package com.emowolf.scm.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

public class DimensionalShieldItem extends Item implements ICurioItem {

    public static final String TAG_SHIELD_VALUE = "ShieldValue";
    public static final String TAG_SHIELD_MAX = "ShieldMax";
    public static final String TAG_REGEN_RATE = "RegenRate";
    public static final String TAG_LOAD = "Load";
    public static final String TAG_LAST_HIT_TIME = "LastHitTime";

    public static final double DEFAULT_MAX_SHIELD = Double.MAX_VALUE - 1;
    public static final double DEFAULT_REGEN_RATE = 0.1;
    public static final double BATTERY_REGEN_BONUS = 0.1;
    public static final double MAX_REGEN_RATE = Integer.MAX_VALUE - 1;

    /** 负载上限 */
    public static final int MAX_LOAD = 256;
    /** 负载衰减间隔（tick），每 2 秒衰减 1 点 */
    private static final int LOAD_DECAY_INTERVAL = 60;
    /** 连续攻击叠加负载的窗口时间（tick），超过此时间未受击则不叠加 */
    private static final int COMBO_WINDOW_TICKS = 100;
    /** 脱战宽限期（tick），超过此时间未受击才开始衰减负载 */
    private static final int DECAY_GRACE_PERIOD_TICKS = 100; // 5 秒

    private static final int REGEN_TICK_INTERVAL = 20; // 每秒回复一次

    public DimensionalShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        CompoundTag tag = stack.getOrCreateTag();

        // 初始化默认值
        if (!tag.contains(TAG_SHIELD_MAX)) {
            tag.putDouble(TAG_SHIELD_MAX, DEFAULT_MAX_SHIELD);
        }
        if (!tag.contains(TAG_REGEN_RATE)) {
            tag.putDouble(TAG_REGEN_RATE, DEFAULT_REGEN_RATE);
        }
        if (!tag.contains(TAG_SHIELD_VALUE)) {
            tag.putDouble(TAG_SHIELD_VALUE, 1.0);
        }

        // 每秒回复护盾（负载 > 0 时禁止回充）
        if (player.level().getGameTime() % REGEN_TICK_INTERVAL == 0) {
            int load = tag.getInt(TAG_LOAD);
            if (load <= 0) {
                double shieldValue = tag.getDouble(TAG_SHIELD_VALUE);
                double maxShield = tag.getDouble(TAG_SHIELD_MAX);
                double regenRate = tag.getDouble(TAG_REGEN_RATE);

                if (shieldValue < maxShield) {
                    shieldValue = Math.min(shieldValue + regenRate, maxShield);
                    tag.putDouble(TAG_SHIELD_VALUE, shieldValue);
                }
            }
        }

        // 负载自然衰减：脱战宽限期后，每 LOAD_DECAY_INTERVAL tick 衰减 1 点
        long lastHitTime = tag.getLong(TAG_LAST_HIT_TIME);
        if (lastHitTime > 0 && (player.level().getGameTime() - lastHitTime) > DECAY_GRACE_PERIOD_TICKS) {
            if (player.level().getGameTime() % LOAD_DECAY_INTERVAL == 0) {
                int load = tag.getInt(TAG_LOAD);
                if (load > 0) {
                    tag.putInt(TAG_LOAD, load - 1);
                }
            }
        }
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(TAG_SHIELD_VALUE)) {
            tag.putDouble(TAG_SHIELD_VALUE, 1.0);
        }
        if (!tag.contains(TAG_SHIELD_MAX)) {
            tag.putDouble(TAG_SHIELD_MAX, DEFAULT_MAX_SHIELD);
        }
        if (!tag.contains(TAG_REGEN_RATE)) {
            tag.putDouble(TAG_REGEN_RATE, DEFAULT_REGEN_RATE);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        CompoundTag tag = stack.getOrCreateTag();
        double shieldValue = tag.getDouble(TAG_SHIELD_VALUE);
        double maxShield = tag.contains(TAG_SHIELD_MAX) ? tag.getDouble(TAG_SHIELD_MAX) : DEFAULT_MAX_SHIELD;
        double regenRate = tag.contains(TAG_REGEN_RATE) ? tag.getDouble(TAG_REGEN_RATE) : DEFAULT_REGEN_RATE;

        tooltipComponents.add(Component.translatable("item.chocomaker.dimensional_shield.desc"));
        tooltipComponents.add(Component.translatable("item.chocomaker.dimensional_shield.value",
                formatCompact(shieldValue), formatCompact(maxShield)));
        tooltipComponents.add(Component.translatable("item.chocomaker.dimensional_shield.regen",
                formatCompact(regenRate)));
        int load = tag.getInt(TAG_LOAD);
        if (load > 0) {
            tooltipComponents.add(Component.translatable("item.chocomaker.dimensional_shield.load",
                    load, MAX_LOAD));
        }
    }

    // ========== 数值压缩格式化 ==========

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

    /**
     * 将数值压缩为可读格式，例如 1234 -> 1.2K, 1.8E308 -> 1.8E308
     */
    public static String formatCompact(double value) {
        if (value < 0) return "-" + formatCompact(-value);
        if (value < 1000) return String.format("%.1f", value);
        if (Double.isInfinite(value)) return "∞";

        // 使用科学计数法处理超大数值
        if (value >= 1e36) {
            return String.format("%.1e", value);
        }

        // 使用后缀缩写
        int tier = (int) (Math.log10(value) / 3);
        if (tier >= SUFFIXES.length) {
            return String.format("%.1e", value);
        }
        double scaled = value / Math.pow(10, tier * 3);
        return String.format("%.1f%s", scaled, SUFFIXES[tier]);
    }

    // ========== 静态辅助方法 ==========

    /**
     * 获取玩家已装备维度护盾的 ItemStack（可能在项链槽位）
     */
    @Nullable
    public static ItemStack findEquippedShield(ServerPlayer player) {
        var result = CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(SCMItems.DIMENSIONAL_SHIELD.get()))
                .orElse(java.util.Optional.empty());
        return result.map(r -> r.stack()).orElse(null);
    }

    /**
     * 从护盾 NBT 读取当前护盾值
     */
    public static double getShieldValue(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return 0;
        return stack.getOrCreateTag().getDouble(TAG_SHIELD_VALUE);
    }

    /**
     * 设置护盾值
     */
    public static void setShieldValue(ItemStack stack, double value) {
        if (stack == null) return;
        double max = stack.getOrCreateTag().getDouble(TAG_SHIELD_MAX);
        stack.getOrCreateTag().putDouble(TAG_SHIELD_VALUE, Math.max(0, Math.min(value, max)));
    }

    /**
     * 获取护盾上限
     */
    public static double getMaxShield(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return DEFAULT_MAX_SHIELD;
        CompoundTag tag = stack.getOrCreateTag();
        return tag.contains(TAG_SHIELD_MAX) ? tag.getDouble(TAG_SHIELD_MAX) : DEFAULT_MAX_SHIELD;
    }

    /**
     * 获取回复速率
     */
    public static double getRegenRate(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return DEFAULT_REGEN_RATE;
        CompoundTag tag = stack.getOrCreateTag();
        return tag.contains(TAG_REGEN_RATE) ? tag.getDouble(TAG_REGEN_RATE) : DEFAULT_REGEN_RATE;
    }

    /**
     * 增加回复速率（按消耗的电池数量批量提升）
     */
    public static void increaseRegenRate(ItemStack stack, int count) {
        if (stack == null || count <= 0) return;
        CompoundTag tag = stack.getOrCreateTag();
        double current = tag.contains(TAG_REGEN_RATE) ? tag.getDouble(TAG_REGEN_RATE) : DEFAULT_REGEN_RATE;
        tag.putDouble(TAG_REGEN_RATE, Math.min(current + BATTERY_REGEN_BONUS * count, MAX_REGEN_RATE));
    }

    /**
     * 获取当前负载值
     */
    public static int getLoad(ItemStack stack) {
        if (stack == null || !stack.hasTag()) return 0;
        return stack.getOrCreateTag().getInt(TAG_LOAD);
    }

    /**
     * 增加负载（受击时调用），连续攻击在窗口期内叠加
     */
    public static void addLoad(ItemStack stack, long gameTime) {
        if (stack == null) return;
        CompoundTag tag = stack.getOrCreateTag();
        long lastHit = tag.getLong(TAG_LAST_HIT_TIME);
        int load = tag.getInt(TAG_LOAD);
        // 在连击窗口期内才叠加，否则重置为 1
        if (lastHit > 0 && (gameTime - lastHit) <= COMBO_WINDOW_TICKS) {
            load = Math.min(load + 1, MAX_LOAD);
        } else {
            load = 1;
        }
        tag.putInt(TAG_LOAD, load);
        tag.putLong(TAG_LAST_HIT_TIME, gameTime);
    }

    /**
     * 计算含负载的额外护盾耗损量
     * @param damage 本次伤害
     * @param load   当前负载
     * @return 额外耗损值
     */
    public static double calcExtraDrain(double damage, int load) {
        return damage * load;
    }
}
