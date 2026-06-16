package com.emowolf.scm.blockentity;

import com.emowolf.scm.SCM;
import com.emowolf.scm.block.SCMBlocks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SCMBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SCM.MODID);

    public static final RegistryObject<BlockEntityType<EternalBeaconBlockEntity>> ETERNAL_BEACON_BE =
            BLOCK_ENTITIES.register("eternal_beacon_be",
                    () -> BlockEntityType.Builder.of(EternalBeaconBlockEntity::new,
                            SCMBlocks.ETERNAL_BEACON.get()).build(null));

    public static final RegistryObject<BlockEntityType<ChocolateAnnihilationGeneratorBlockEntity>> CHOCOLATE_ANNIHILATION_GENERATOR_BE =
            BLOCK_ENTITIES.register("chocolate_annihilation_generator_be",
                    () -> BlockEntityType.Builder.of(ChocolateAnnihilationGeneratorBlockEntity::new,
                            SCMBlocks.CHOCOLATE_ANNIHILATION_GENERATOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<HyperCannonControlCenterBlockEntity>> HYPER_CANNON_CONTROL_CENTER_BE =
            BLOCK_ENTITIES.register("hyper_cannon_control_center_be",
                    () -> BlockEntityType.Builder.of(HyperCannonControlCenterBlockEntity::new,
                            SCMBlocks.HYPER_CANNON_CONTROL_CENTER.get()).build(null));

    public static final RegistryObject<BlockEntityType<FoodReplicatorBlockEntity>> FOOD_REPLICATOR_BE =
            BLOCK_ENTITIES.register("food_replicator_be",
                    () -> BlockEntityType.Builder.of(FoodReplicatorBlockEntity::new,
                            SCMBlocks.FOOD_REPLICATOR.get()).build(null));

    public static final RegistryObject<BlockEntityType<ChocolateMixerBlockEntity>> CHOCOLATE_MIXER_BE =
            BLOCK_ENTITIES.register("chocolate_mixer_be",
                    () -> BlockEntityType.Builder.of(ChocolateMixerBlockEntity::new,
                            SCMBlocks.CHOCOLATE_MIXER.get()).build(null));

    /** Factory method to create EternalBeaconMenu without circular dependency */
    public static AbstractContainerMenu createBeaconMenu(int containerId, Inventory playerInventory,
                                                          EternalBeaconBlockEntity be) {
        return new com.emowolf.scm.inventory.EternalBeaconMenu(containerId, playerInventory, be);
    }

    /** Factory method to create ChocolateAnnihilationGeneratorMenu without circular dependency */
    public static AbstractContainerMenu createGeneratorMenu(int containerId, Inventory playerInventory,
                                                             ChocolateAnnihilationGeneratorBlockEntity be) {
        return new com.emowolf.scm.inventory.ChocolateAnnihilationGeneratorMenu(containerId, playerInventory, be);
    }

    /** Factory method to create HyperCannonControlCenterMenu without circular dependency */
    public static AbstractContainerMenu createHyperCannonMenu(int containerId, Inventory playerInventory,
                                                                HyperCannonControlCenterBlockEntity be) {
        return new com.emowolf.scm.inventory.HyperCannonControlCenterMenu(containerId, playerInventory, be);
    }

    /** Factory method to create FoodReplicatorMenu without circular dependency */
    public static AbstractContainerMenu createFoodReplicatorMenu(int containerId, Inventory playerInventory,
                                                                  FoodReplicatorBlockEntity be) {
        return new com.emowolf.scm.inventory.FoodReplicatorMenu(containerId, playerInventory, be);
    }

    /** Factory method to create ChocolateMixerMenu without circular dependency */
    public static AbstractContainerMenu createChocolateMixerMenu(int containerId, Inventory playerInventory,
                                                                  ChocolateMixerBlockEntity be) {
        return new com.emowolf.scm.inventory.ChocolateMixerMenu(containerId, playerInventory, be);
    }
}
