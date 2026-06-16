package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.block.SCMBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 将食物复制机注入所有原版奖励箱的战利品表
 * 概率略低于呆毛（weight=1 vs 呆毛 weight=2）
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FoodReplicatorLootHandler {

    /** 食物复制机出现在箱子中的权重，略低于呆毛(2) */
    private static final int FOOD_REPLICATOR_WEIGHT = 1;

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        ResourceLocation name = event.getName();

        // 只处理原版箱子战利品表
        if (!"minecraft".equals(name.getNamespace())) {
            return;
        }

        String path = name.getPath();
        if (!path.startsWith("chests/")) {
            return;
        }

        event.getTable().addPool(
                LootPool.lootPool()
                        .name("chocomaker:food_replicator_inject")
                        .setRolls(BinomialDistributionGenerator.binomial(1, 0.25F))
                        .add(LootItem.lootTableItem(SCMBlocks.FOOD_REPLICATOR_ITEM.get())
                                .setWeight(FOOD_REPLICATOR_WEIGHT))
                        .build()
        );
    }
}
