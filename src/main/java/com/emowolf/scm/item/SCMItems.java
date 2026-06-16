package com.emowolf.scm.item;

import com.emowolf.scm.SCM;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SCMItems {
    /** Energy provided by one chocolate item */
    public static final int ENERGY_PER_CHOCOLATE = 10;
    /** Energy provided by one chocolate battery */
    public static final int ENERGY_PER_CHOCOLATE_BATTERY = 50;

    /** FE conversion: 1 chocolate energy = 2 FE */
    public static final int FE_PER_CHOCOLATE_ENERGY = 2;

    /**
     * 计算一个 ItemStack 作为巧克力能源能提供的能量总量（每单位能量 × 堆叠数量）。
     * 返回值为巧克力能量单位，如需 FE 请使用 {@link #getEnergyValueFE}。
     * @param stack 待检测的物品堆叠
     * @return 能量总量；若物品不是巧克力或巧克力电池则返回 0
     */
    public static int getEnergyValue(ItemStack stack) {
        if (stack.is(CHOCOLATE_BATTERY.get())) {
            return ENERGY_PER_CHOCOLATE_BATTERY * stack.getCount();
        } else if (stack.is(CHOCOLATE.get())) {
            return ENERGY_PER_CHOCOLATE * stack.getCount();
        }
        return 0;
    }

    /**
     * 计算一个 ItemStack 能提供的 FE 能量总量（每单位能量 × 堆叠数量 × FE换算率）。
     * @param stack 待检测的物品堆叠
     * @return FE 能量总量；若物品不是巧克力或巧克力电池则返回 0
     */
    public static int getEnergyValueFE(ItemStack stack) {
        return getEnergyValue(stack) * FE_PER_CHOCOLATE_ENERGY;
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SCM.MODID);

    public static final RegistryObject<Item> FLATTEN_TOOL = ITEMS.register("flatten_tool",
            () -> new FlattenToolItem(new Item.Properties().stacksTo(1).durability(0)));
    
    public static final RegistryObject<Item> CHOCOLATE = ITEMS.register("chocolate",
            ChocolateItem::new);

    public static final RegistryObject<Item> GOLDEN_CHOCOLATE = ITEMS.register("golden_chocolate",
            GoldenChocolateItem::new);

    public static final RegistryObject<Item> FLIGHT_CHARM = ITEMS.register("flight_charm",
            () -> new FlightCharmItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BEN_SHOU_MIAO_SHOU_CHAO_SHOU = ITEMS.register("ben_shou_miao_shou_chao_shou",
            () -> new BenShouMiaoShouChaoShouItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> TELEPORT_CHARM = ITEMS.register("teleport_charm",
            () -> new TeleportCharmItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> AHOGE = ITEMS.register("ahoge",
            AhogeItem::new);

    public static final RegistryObject<Item> CHOCOLATE_BATTERY = ITEMS.register("chocolate_battery",
            () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> DIMENSIONAL_SHIELD = ITEMS.register("dimensional_shield",
            () -> new DimensionalShieldItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> MILK_CHOCOLATE = ITEMS.register("milk_chocolate",
            MilkChocolateItem::new);

    public static final RegistryObject<Item> AGED_CHOCOLATE = ITEMS.register("aged_chocolate",
            AgedChocolateItem::new);

    public static final RegistryObject<Item> ENCHANTED_GOLDEN_CHOCOLATE = ITEMS.register("enchanted_golden_chocolate",
            EnchantedGoldenChocolateItem::new);

    public static final RegistryObject<Item> MACHINE_GUN = ITEMS.register("machine_gun",
            () -> new MachineGunItem(new Item.Properties()));

    public static final RegistryObject<Item> BOTTLED_RAINBOW = ITEMS.register("bottled_rainbow",
            () -> new BottledRainbowItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> RACKET = ITEMS.register("racket",
            () -> new RacketItem(new Item.Properties()));

    public static final RegistryObject<Item> MIXED_CHOCOLATE = ITEMS.register("mixed_chocolate",
            MixedChocolateItem::new);

    public static final RegistryObject<Item> BATTERY_BOX = ITEMS.register("battery_box",
            () -> new BatteryBoxItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> NIGHTCAP = ITEMS.register("nightcap",
            () -> new NightcapItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> REACH_CHARM = ITEMS.register("reach_charm",
            () -> new ReachCharmItem(new Item.Properties().stacksTo(1)));
}
