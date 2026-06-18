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
 * 将附魔金巧克力注入所有原版奖励箱的战利品表
 * 稀有程度接近附魔金苹果（仅5%箱子触发roll，weight=1）
 */
@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnchantedGoldenChocolateLootHandler {

    /** 附魔金巧克力出现在箱子中的权重，接近附魔金苹果稀有度 */
    private static final int WEIGHT = 1;

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
                        .name("chocomaker:enchanted_golden_chocolate_inject")
                        .setRolls(BinomialDistributionGenerator.binomial(1, 0.05F))
                        .add(LootItem.lootTableItem(SCMItems.ENCHANTED_GOLDEN_CHOCOLATE.get())
                                .setWeight(WEIGHT))
                        .build()
        );
    }
}
