package com.emowolf.scm.client;

import com.emowolf.scm.SCM;
import com.emowolf.scm.block.SCMBlocks;
import com.emowolf.scm.blockentity.SCMBlockEntities;
import com.emowolf.scm.client.model.MixedChocolateBakedModel;
import com.emowolf.scm.client.screen.ChocolateAnnihilationGeneratorScreen;
import com.emowolf.scm.client.screen.ChocolateMixerScreen;
import com.emowolf.scm.client.screen.EternalBeaconScreen;
import com.emowolf.scm.client.screen.FoodReplicatorScreen;
import com.emowolf.scm.client.screen.HyperCannonControlCenterScreen;
import com.emowolf.scm.inventory.SCMMenus;
import com.emowolf.scm.item.BatteryBoxItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.Map;

@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(SCMMenus.ETERNAL_BEACON_MENU.get(), EternalBeaconScreen::new);
            MenuScreens.register(SCMMenus.CHOCOLATE_ANNIHILATION_GENERATOR_MENU.get(), ChocolateAnnihilationGeneratorScreen::new);
            MenuScreens.register(SCMMenus.HYPER_CANNON_CONTROL_CENTER_MENU.get(), HyperCannonControlCenterScreen::new);
            MenuScreens.register(SCMMenus.FOOD_REPLICATOR_MENU.get(), FoodReplicatorScreen::new);
            MenuScreens.register(SCMMenus.CHOCOLATE_MIXER_MENU.get(), ChocolateMixerScreen::new);
            BlockEntityRenderers.register(SCMBlockEntities.ETERNAL_BEACON_BE.get(), EternalBeaconRenderer::new);

            // 永恒信标使用原版信标模型（含玻璃），需要 cutout 渲染层
            ItemBlockRenderTypes.setRenderLayer(SCMBlocks.ETERNAL_BEACON.get(), RenderType.cutout());

            // 电池盒纹饰 model predicate
            ItemProperties.register(SCMItems.BATTERY_BOX.get(),
                    new ResourceLocation(SCM.MODID, "style"),
                    (stack, level, entity, seed) -> BatteryBoxItem.getStyle(stack));
        });
    }

    /**
     * 替换混合巧克力的 BakedModel，使其支持 mix_level 动态缩放。
     */
    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (Map.Entry<ResourceLocation, BakedModel> entry : models.entrySet()) {
            ResourceLocation key = entry.getKey();
            if (SCM.MODID.equals(key.getNamespace()) && "mixed_chocolate".equals(key.getPath())) {
                models.put(key, new MixedChocolateBakedModel(entry.getValue()));
            }
        }
    }
}
