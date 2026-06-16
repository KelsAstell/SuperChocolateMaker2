package com.emowolf.scm.event;

import com.emowolf.scm.Configs;
import com.emowolf.scm.SCM;
import com.emowolf.scm.compat.CuriosCompat;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class FlyEventHandler {

    private static final String CURIOS_MODID = "curios";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) { grantAndSync(event.getEntity()); }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) { grantAndSync(event.getEntity()); }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) { grantAndSync(event.getEntity()); }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Player player) grantAndSync(player);
    }

    // 节流计数器，每 20 tick（1 秒）检测一次
    private static int tickCounter = 0;

    /** 飞行解锁缓存，每 100 tick（5秒）刷新 */
    private static boolean cachedFlightUnlocked = false;
    private static long flightCacheExpiry = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Configs.COMMON.enableFlying.get()) return;
        if (++tickCounter % 20 != 0) return;

        net.minecraft.server.MinecraftServer server = event.getServer();
        if (server == null) return;

        // 使用缓存检查仪式解锁状态
        if (!isFlightUnlocked(server)) return;

        double cost = Configs.COMMON.flyHungerCostPerSecond.get();
        boolean curiosLoaded = ModList.get().isLoaded(CURIOS_MODID);

        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            // 确保玩家拥有飞行权限
            if (!sp.getAbilities().mayfly) {
                sp.getAbilities().mayfly = true;
                sp.onUpdateAbilities();
                sp.connection.send(new ClientboundPlayerAbilitiesPacket(sp.getAbilities()));
            }

            // 玩家正在飞行时消耗饱食度
            if (sp.getAbilities().flying && cost > 0.0) {
                // 装备飞行护符时不消耗饱食度
                boolean hasCharm = curiosLoaded && CuriosCompat.hasFlightCharmEquipped(sp);

                if (!hasCharm) {
                    int foodLevel = sp.getFoodData().getFoodLevel();

                    if (foodLevel <= 0) {
                        // 饱食度不足，强制取消飞行
                        sp.getAbilities().flying = false;
                        sp.onUpdateAbilities();
                        sp.connection.send(new ClientboundPlayerAbilitiesPacket(sp.getAbilities()));
                    } else {
                        // 通过 exhaustion 机制消耗饱食度（先消耗饱和度，再降低饥饿值）
                        sp.causeFoodExhaustion((float) cost);
                    }
                }
            }
        }
    }

    private static void grantAndSync(Player player) {
        if (player.level().isClientSide()) return;
        if (!Configs.COMMON.enableFlying.get()) return;
        if (!(player instanceof ServerPlayer sp)) return;

        // 使用缓存检查仪式解锁状态
        if (!isFlightUnlocked(sp.server)) return;

        if (!sp.getAbilities().mayfly) {
            sp.getAbilities().mayfly = true;
            sp.onUpdateAbilities();
        }

        sp.connection.send(new ClientboundPlayerAbilitiesPacket(sp.getAbilities()));
        sp.server.execute(() -> {
            if (!sp.getAbilities().mayfly) {
                sp.getAbilities().mayfly = true;
                sp.onUpdateAbilities();
            }
            sp.connection.send(new ClientboundPlayerAbilitiesPacket(sp.getAbilities()));
        });
    }

    private static boolean isFlightUnlocked(net.minecraft.server.MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now >= flightCacheExpiry) {
            cachedFlightUnlocked = RitualUnlockData.get(server.overworld()).isFlightUnlocked();
            flightCacheExpiry = now + 100; // 每 5 秒刷新
        }
        return cachedFlightUnlocked;
    }
}
