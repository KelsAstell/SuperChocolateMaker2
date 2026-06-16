package com.emowolf.scm.effect;

import com.emowolf.scm.SCM;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class SCMEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, SCM.MODID);

    public static final RegistryObject<MobEffect> IMAGINARY_POLLUTION =
            EFFECTS.register("imaginary_pollution", ImaginaryPollutionEffect::new);

    public static final RegistryObject<MobEffect> IMAGINARY_RESISTANCE =
            EFFECTS.register("imaginary_resistance", ImaginaryResistanceEffect::new);
}
