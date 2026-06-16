package com.emowolf.scm.block;

import com.emowolf.scm.SCM;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SCMBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, SCM.MODID);
    public static final DeferredRegister<Item> BLOCK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, SCM.MODID);

    public static final RegistryObject<Block> ALTAR_CORE = BLOCKS.register("altar_core",
            () -> new AltarCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> ALTAR_CORE_ITEM = BLOCK_ITEMS.register("altar_core",
            () -> new BlockItem(ALTAR_CORE.get(), new Item.Properties()));

    public static final RegistryObject<Block> ETERNAL_BEACON = BLOCKS.register("eternal_beacon",
            () -> new EternalBeaconBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIAMOND)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .lightLevel(s -> 15)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> ETERNAL_BEACON_ITEM = BLOCK_ITEMS.register("eternal_beacon",
            () -> new BlockItem(ETERNAL_BEACON.get(), new Item.Properties()));

    public static final RegistryObject<Block> CHOCOLATE_ANNIHILATION_GENERATOR = BLOCKS.register("chocolate_annihilation_generator",
            () -> new ChocolateAnnihilationGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> CHOCOLATE_ANNIHILATION_GENERATOR_ITEM = BLOCK_ITEMS.register("chocolate_annihilation_generator",
            () -> new BlockItem(CHOCOLATE_ANNIHILATION_GENERATOR.get(), new Item.Properties()));

    public static final RegistryObject<Block> HYPER_CANNON_CONTROL_CENTER = BLOCKS.register("hyper_cannon_control_center",
            () -> new HyperCannonControlCenterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> HYPER_CANNON_CONTROL_CENTER_ITEM = BLOCK_ITEMS.register("hyper_cannon_control_center",
            () -> new BlockItem(HYPER_CANNON_CONTROL_CENTER.get(), new Item.Properties()));

    public static final RegistryObject<Block> FOOD_REPLICATOR = BLOCKS.register("food_replicator",
            () -> new FoodReplicatorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> FOOD_REPLICATOR_ITEM = BLOCK_ITEMS.register("food_replicator",
            () -> new BlockItem(FOOD_REPLICATOR.get(), new Item.Properties()));

    public static final RegistryObject<Block> CHOCOLATE_MIXER = BLOCKS.register("chocolate_mixer",
            () -> new ChocolateMixerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(5.0f, 1200.0f)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Item> CHOCOLATE_MIXER_ITEM = BLOCK_ITEMS.register("chocolate_mixer",
            () -> new BlockItem(CHOCOLATE_MIXER.get(), new Item.Properties()));
}
