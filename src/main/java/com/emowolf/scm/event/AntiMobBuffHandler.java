package com.emowolf.scm.event;

import com.emowolf.scm.Configs;
import com.emowolf.scm.SCM;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = SCM.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AntiMobBuffHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final UUID RANDOM_HEALTH_UUID = UUID.fromString("f3bbe254-3008-48cf-9774-f69d1e81d16b");
    private static final UUID RANDOM_DAMAGE_UUID = UUID.fromString("88b9a4ec-7cb6-4533-bed3-69ee0e823fd3");

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Monster living)) return;
        
        // 检查配置是否启用
        if (!Configs.COMMON.enableRemoveMobBuffs.get()) return;
        
        // 检查仪式是否已解锁
        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        RitualUnlockData data = RitualUnlockData.get(serverLevel);
        if (!data.isAntiMobBuffUnlocked()) return;
        
        AttributeInstance maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(RANDOM_HEALTH_UUID) != null) {
            maxHealth.removeModifier(RANDOM_HEALTH_UUID);
            float currentMax = (float) maxHealth.getValue();
            if (living.getHealth() > currentMax) {
                living.setHealth(currentMax);
            }
        }

        AttributeInstance attackDamage = living.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null && attackDamage.getModifier(RANDOM_DAMAGE_UUID) != null) {
            attackDamage.removeModifier(RANDOM_DAMAGE_UUID);
        }

        if (!living.getActiveEffects().isEmpty()) {
            boolean hadBeneficial = living.getActiveEffects().stream()
                    .anyMatch(inst -> inst.getEffect().isBeneficial());
            if (hadBeneficial) {
                living.removeAllEffects();
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 102400, 0, false, false));
            }
        }
    }
}
