package com.emowolf.scm.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 虚数污染 - 使玩家陷入虚弱、缓慢、凋零、中毒，且无法通过喝牛奶解除
 */
public class ImaginaryPollutionEffect extends MobEffect {

    /** 子效果的持续 ticks（10秒，每次刷新） */
    private static final int SUB_EFFECT_DURATION = 200;
    /** 每隔多少 tick 刷新一次子效果 */
    private static final int REFRESH_INTERVAL = 40;

    private static final MobEffect[] SUB_EFFECTS = {
            MobEffects.WEAKNESS,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.WITHER,
            MobEffects.POISON
    };

    public ImaginaryPollutionEffect() {
        super(MobEffectCategory.HARMFUL, 0x4B0082); // 靛紫色
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) return;

        if (entity.tickCount % REFRESH_INTERVAL == 0) {
            for (MobEffect effect : SUB_EFFECTS) {
                MobEffectInstance existing = entity.getEffect(effect);
                // 只在子效果不存在或即将过期时刷新，避免覆盖外部施加的同名效果
                if (existing == null || existing.getDuration() < REFRESH_INTERVAL + 20) {
                    entity.addEffect(new MobEffectInstance(effect, SUB_EFFECT_DURATION, 0, false, false, true));
                }
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true; // 每 tick 都调用 applyEffectTick
    }
}
