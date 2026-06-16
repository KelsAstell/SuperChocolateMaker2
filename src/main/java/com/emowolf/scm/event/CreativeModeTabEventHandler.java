package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.block.SCMBlocks;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CreativeModeTabEventHandler {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, SCM.MODID);

    public static final RegistryObject<CreativeModeTab> SCM_TAB = TABS.register("scm_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chocomaker"))
                    .icon(() -> new ItemStack(SCMItems.CHOCOLATE.get()))
                    .displayItems((params, output) -> {
                        // 所有物品
                        SCMItems.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                        // 所有方块物品
                        SCMBlocks.BLOCK_ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    })
                    .build());

    @SubscribeEvent
    public static void buildContents(BuildCreativeModeTabContentsEvent event) {
        // 不再向原版标签页添加物品，统一放入专属标签页
    }
}
