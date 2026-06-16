package com.emowolf.scm.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 虚数抵抗 - 正面效果，持有该效果时免疫虚数污染的施加
 */
public class ImaginaryResistanceEffect extends MobEffect {

    public ImaginaryResistanceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFFD700); // 金色
    }
}
