package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.compat.CuriosCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class EmptyHandDropHandler {
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onEmptyHandAttack(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        
        // 检查是否装备了本手妙手抄手饰品
        boolean hasBenShouMiaoShouChaoShou = false;
        if (FMLLoader.getLoadingModList().getModFileById("curios") != null && player instanceof ServerPlayer serverPlayer) {
            hasBenShouMiaoShouChaoShou = CuriosCompat.hasBenShouMiaoShouChaoShouEquipped(serverPlayer);
        }
        if (!hasBenShouMiaoShouChaoShou) return;
        
        if (!player.getMainHandItem().isEmpty()) return;

        LivingEntity target = event.getEntity();
        if (!(target instanceof Monster monster)) return;

        // 25% 概率触发掉落
        if (RANDOM.nextFloat() >= 0.25f) return;

        // 尝试从怪物装备栏掉落
        List<EquipmentSlot> slots = Arrays.stream(EquipmentSlot.values())
                .filter(slot -> !monster.getItemBySlot(slot).isEmpty())
                .toList();

        if (!slots.isEmpty()) {
            EquipmentSlot slot = slots.get(RANDOM.nextInt(slots.size()));
            ItemStack stack = monster.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                monster.spawnAtLocation(stack.copy(), 0.0f);
                monster.setItemSlot(slot, ItemStack.EMPTY);
            }
        } else {
            // 否则从怪物掉落表中随机掉落
            Level level = monster.level();
            if (!(level instanceof ServerLevel serverLevel)) return;

            ResourceLocation lootTableId = monster.getLootTable();
            LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(lootTableId);

            // 参照 LivingEntity.dropFromLootTable() 构建完整的 LootParams
            LootParams.Builder paramsBuilder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.THIS_ENTITY, monster)
                    .withParameter(LootContextParams.ORIGIN, monster.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, player)
                    .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                    .withLuck(player.getLuck());

            LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
            List<ItemStack> drops = lootTable.getRandomItems(params);

            if (!drops.isEmpty()) {
                ItemStack drop = drops.get(RANDOM.nextInt(drops.size()));
                if (!drop.isEmpty()) {
                    monster.spawnAtLocation(drop, 0.0f);
                }
            }
        }
    }
}
