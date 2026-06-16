package com.emowolf.scm.inventory;

import com.emowolf.scm.SCM;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SCMMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, SCM.MODID);

    public static final RegistryObject<MenuType<EternalBeaconMenu>> ETERNAL_BEACON_MENU =
            MENUS.register("eternal_beacon_menu",
                    () -> IForgeMenuType.create(EternalBeaconMenu::new));

    public static final RegistryObject<MenuType<ChocolateAnnihilationGeneratorMenu>> CHOCOLATE_ANNIHILATION_GENERATOR_MENU =
            MENUS.register("chocolate_annihilation_generator_menu",
                    () -> IForgeMenuType.create(ChocolateAnnihilationGeneratorMenu::new));

    public static final RegistryObject<MenuType<HyperCannonControlCenterMenu>> HYPER_CANNON_CONTROL_CENTER_MENU =
            MENUS.register("hyper_cannon_control_center_menu",
                    () -> IForgeMenuType.create(HyperCannonControlCenterMenu::new));

    public static final RegistryObject<MenuType<FoodReplicatorMenu>> FOOD_REPLICATOR_MENU =
            MENUS.register("food_replicator_menu",
                    () -> IForgeMenuType.create(FoodReplicatorMenu::new));

    public static final RegistryObject<MenuType<ChocolateMixerMenu>> CHOCOLATE_MIXER_MENU =
            MENUS.register("chocolate_mixer_menu",
                    () -> IForgeMenuType.create(ChocolateMixerMenu::new));
}
