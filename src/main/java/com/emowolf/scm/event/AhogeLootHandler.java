package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 将呆毛注入所有原版奖励箱的战利品表
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AhogeLootHandler {

    /** 呆毛出现在箱子中的权重，越高越容易出现 */
    private static final int AHOGE_WEIGHT = 2;

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

        // 排除奖励箱中过于稀有/特殊的类型
        // buried_treasure 是埋藏的宝藏，spawn_bonus_chest 是奖励箱（如果开局没开就不会再生）
        // 都包含进去增加获取途径

        event.getTable().addPool(
                LootPool.lootPool()
                        .name("chocomaker:ahoge_inject")
                        .setRolls(BinomialDistributionGenerator.binomial(1, 0.25F))
                        .add(LootItem.lootTableItem(SCMItems.AHOGE.get())
                                .setWeight(AHOGE_WEIGHT))
                        .build()
        );
    }
}
