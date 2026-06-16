package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.compat.CuriosCompat;
import com.emowolf.scm.item.BatteryBoxItem;
import com.emowolf.scm.item.DimensionalShieldItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class BatteryBoxHandler {

    /**
     * 拾取巧克力/巧克力电池时，自动吸收到手持的电池盒中。
     * 仅主手持有电池盒时触发。
     */
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemEntity itemEntity = event.getItem();
        ItemStack pickupStack = itemEntity.getItem();

        // 只吸收巧克力或巧克力电池
        if (!pickupStack.is(SCMItems.CHOCOLATE.get()) && !pickupStack.is(SCMItems.CHOCOLATE_BATTERY.get())) return;

        // 仅主手持有电池盒时才吸收
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(SCMItems.BATTERY_BOX.get())) return;

        int count = pickupStack.getCount();
        int absorbed = BatteryBoxItem.absorbItem(mainHand, pickupStack);

        if (absorbed > 0) {
            itemEntity.discard();
            event.setCanceled(true);

            double feOutput = BatteryBoxItem.getFEOutput(mainHand);
            serverPlayer.sendSystemMessage(Component.translatable("msg.chocomaker.battery_box.absorbed",
                    DimensionalShieldItem.formatCompact(feOutput)));
        }
    }

    /**
     * 每 tick 将电池盒的 FE 产出主动推送给背包中可充电的物品。
     * 电池盒需装备在 Curios belt 栏位才能供能；手持仅用于吸收巧克力/电池。
     * 电池盒不储存能量，仅按当前功率（FE/t）对外输出。
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer serverPlayer)) return;
        if (!ModList.get().isLoaded("curios")) return;

        ItemStack batteryBox = CuriosCompat.findEquippedBatteryBox(serverPlayer);
        if (batteryBox.isEmpty()) return;

        double feOutput = BatteryBoxItem.getFEOutput(batteryBox);
        if (feOutput <= 0.0) return;

        // 分数累加：浮点产出逐 tick 累积，达到整数后对外推送
        double accumulated = BatteryBoxItem.getFeAccumulator(batteryBox) + feOutput;
        int available = (int) accumulated;
        double remainder = accumulated - available;
        BatteryBoxItem.setFeAccumulator(batteryBox, remainder);

        if (available <= 0) return;

        // 遍历玩家背包，向可接收 FE 的物品推送能量
        int remaining = available;

        // 1) 快捷栏 (9 格)
        remaining = chargeInventory(serverPlayer.getInventory().items.subList(0, 9), remaining);
        if (remaining <= 0) return;

        // 2) 盔甲栏 (4 格)
        remaining = chargeInventory(serverPlayer.getInventory().armor, remaining);
        if (remaining <= 0) return;

        // 3) 副手
        remaining = chargeSingleSlot(serverPlayer.getOffhandItem(), remaining);
    }

    /**
     * 向物品列表中的可充电物品推送能量。
     * @return 剩余未分配的能量
     */
    private static int chargeInventory(java.util.List<ItemStack> items, int available) {
        for (ItemStack stack : items) {
            if (available <= 0) break;
            available = chargeSingleSlot(stack, available);
        }
        return available;
    }

    /**
     * 向单个物品推送能量。
     * @return 剩余未分配的能量
     */
    private static int chargeSingleSlot(ItemStack stack, int available) {
        if (stack.isEmpty() || available <= 0) return available;
        return stack.getCapability(ForgeCapabilities.ENERGY).map(receiver -> {
            if (!receiver.canReceive()) return available;
            int accepted = receiver.receiveEnergy(available, false);
            return available - accepted;
        }).orElse(available);
    }
}
