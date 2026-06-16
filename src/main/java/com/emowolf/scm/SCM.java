package com.emowolf.scm;

import com.emowolf.scm.block.SCMBlocks;
import com.emowolf.scm.blockentity.SCMBlockEntities;
import com.emowolf.scm.effect.SCMEffects;
import com.emowolf.scm.inventory.SCMMenus;
import com.emowolf.scm.item.SCMItems;
import com.emowolf.scm.event.CreativeModeTabEventHandler;
import com.emowolf.scm.network.SCMNetwork;
import com.emowolf.scm.recipe.SCMRecipeSerializers;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SCM.MODID)
public class SCM {
    public static final String MODID = "chocomaker";
    private static final Logger LOGGER = LogUtils.getLogger();

    public SCM() {
        ModLoadingContext.get().registerConfig(Type.COMMON, Configs.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(Type.CLIENT, Configs.CLIENT_SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        
        // 注册物品与方块
        SCMItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SCMBlocks.BLOCKS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SCMBlocks.BLOCK_ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SCMBlockEntities.BLOCK_ENTITIES.register(FMLJavaModLoadingContext.get().getModEventBus());
        SCMMenus.MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SCMEffects.EFFECTS.register(FMLJavaModLoadingContext.get().getModEventBus());
        CreativeModeTabEventHandler.TABS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SCMRecipeSerializers.RECIPE_SERIALIZERS.register(FMLJavaModLoadingContext.get().getModEventBus());
        
        // 注册网络通道
        SCMNetwork.register();
        
        LOGGER.info("[{}] ===============================", MODID);
        LOGGER.info("[{}]   SuperChocolateMaker2 - By Kels_Astell  ", MODID);
        LOGGER.info("[{}] ===============================", MODID);
    }

}
