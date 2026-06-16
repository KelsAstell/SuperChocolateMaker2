package com.emowolf.scm.compat;

import com.emowolf.scm.item.SCMItems;
import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;

public class CuriosCompat {

    /**
     * 检查玩家是否装备了飞行护符（Curios饰品）
     * 此方法仅在 Curios 模组加载时被调用，避免 NoClassDefFoundError
     */
    public static boolean hasFlightCharmEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(SCMItems.FLIGHT_CHARM.get()))
                .orElse(false);
    }

    /**
     * 检查玩家是否装备了手（Curios饰品）
     * 此方法仅在 Curios 模组加载时被调用，避免 NoClassDefFoundError
     */
    public static boolean hasBenShouMiaoShouChaoShouEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(SCMItems.BEN_SHOU_MIAO_SHOU_CHAO_SHOU.get()))
                .orElse(false);
    }

    /**
     * 检查玩家是否装备了传送护符（Curios饰品）
     * 此方法仅在 Curios 模组加载时被调用，避免 NoClassDefFoundError
     */
    public static boolean hasTeleportCharmEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(SCMItems.TELEPORT_CHARM.get()))
                .orElse(false);
    }

    /**
     * 检查玩家是否装备了维度护盾（Curios饰品）
     * 此方法仅在 Curios 模组加载时被调用，避免 NoClassDefFoundError
     */
    public static boolean hasDimensionalShieldEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(SCMItems.DIMENSIONAL_SHIELD.get()))
                .orElse(false);
    }

    /**
     * 检查玩家是否装备了距离护符（Curios饰品）
     * 此方法仅在 Curios 模组加载时被调用，避免 NoClassDefFoundError
     */
    public static boolean hasReachCharmEquipped(ServerPlayer player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(SCMItems.REACH_CHARM.get()))
                .orElse(false);
    }

    /**
     * 查找玩家 Curios belt 栏位中装备的电池盒。
     * 此方法仅在 Curios 模组加载时被调用，避免 NoClassDefFoundError
     * @return 装备的电池盒 ItemStack，未装备时返回 null
     */
    public static net.minecraft.world.item.ItemStack findEquippedBatteryBox(ServerPlayer player) {
        var result = CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.findFirstCurio(SCMItems.BATTERY_BOX.get()))
                .orElse(java.util.Optional.empty());
        return result.map(r -> r.stack()).orElse(net.minecraft.world.item.ItemStack.EMPTY);
    }
}
