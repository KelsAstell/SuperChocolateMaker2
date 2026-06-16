package com.emowolf.scm;

import net.minecraftforge.common.ForgeConfigSpec;

public class Configs {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        final ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new Common(commonBuilder);
        COMMON_SPEC = commonBuilder.build();

        final ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    public static class Common {
        public final ForgeConfigSpec.BooleanValue enableFlying;
        public final ForgeConfigSpec.DoubleValue  flyHungerCostPerSecond;
        public final ForgeConfigSpec.BooleanValue enableInfiniteWater;
        public final ForgeConfigSpec.BooleanValue enableInfiniteLava;
        public final ForgeConfigSpec.BooleanValue enableRemoveMobBuffs;
        public final ForgeConfigSpec.BooleanValue enableAnAppleADay;
        public final ForgeConfigSpec.BooleanValue enableTp2p;
        public final ForgeConfigSpec.BooleanValue enableTpUp;
        public final ForgeConfigSpec.BooleanValue enableNightVision;
        public final ForgeConfigSpec.BooleanValue enableUnbreakable;
        public final ForgeConfigSpec.DoubleValue  emptyHandDropChance;
        public final ForgeConfigSpec.BooleanValue resetChocolateOnDeath;
        public final ForgeConfigSpec.BooleanValue flattenToolSurvivalAllowed;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("QoL");
            enableFlying = builder
                    .comment("Enable flying for all players.")
                    .define("enableFlying", true);
            flyHungerCostPerSecond = builder
                    .comment("Hunger points consumed per second while flying. Set to 0 to disable hunger cost.")
                    .defineInRange("flyHungerCostPerSecond", 0.5, 0.0, 20.0);
            enableTp2p = builder
                    .comment("Enable /tp2p command.")
                    .define("enableTp2p", true);
            enableTpUp = builder
                    .comment("Enable /tpup command.")
                    .define("enableTpUp", true);
            enableNightVision = builder
                    .comment("Enable /nv command.")
                    .define("enableNightVision", true);
            enableUnbreakable = builder
                    .comment("Enable /unbreakable command.")
                    .define("enableUnbreakable", true);
            enableAnAppleADay = builder
                    .comment("Eating apple/cookie will instantly heal player.")
                    .define("enableAnAppleADay", true);
            resetChocolateOnDeath = builder
                    .comment("Reset chocolate effect on player death.")
                    .define("resetChocolateOnDeath", true);
            flattenToolSurvivalAllowed = builder
                    .comment("Allow survival mode players to use the Flatten Tool. Default: false (creative only).")
                    .define("flattenToolSurvivalAllowed", false);
            builder.pop();

            builder.push("Infinite_Fluid");
            enableInfiniteWater = builder
                    .comment("Enable infinite water behavior.")
                    .define("enableInfiniteWater", true);
            enableInfiniteLava = builder
                    .comment("Enable infinite lava behavior.")
                    .define("enableInfiniteLava", true);
            builder.pop();


            builder.push("Mob");
            enableRemoveMobBuffs = builder
                    .comment("Remove random health/damage buffs from mobs.")
                    .define("enableRemoveMobBuffs", true);
            emptyHandDropChance = builder
                    .comment("Monsters can be looted by player with empty hands.\nOnly effective in PVE.")
                    .defineInRange("emptyHandDropChance", 0.25, 0.0, 1.0);
            builder.pop();
        }
    }

    public static class Client {
        public final ForgeConfigSpec.IntValue hudOffsetX;
        public final ForgeConfigSpec.IntValue hudOffsetY;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("dimensional_shield_hud");
            hudOffsetX = builder
                    .comment("Horizontal offset of the Dimensional Shield HUD from the left edge of the screen.")
                    .defineInRange("hudOffsetX", 8, 0, 4096);
            hudOffsetY = builder
                    .comment("Vertical offset of the Dimensional Shield HUD from the bottom edge of the screen.")
                    .defineInRange("hudOffsetY", 36, 0, 4096);
            builder.pop();
        }
    }
}
