package com.emowolf.scm.event;

import com.emowolf.scm.SCM;
import com.emowolf.scm.Configs;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class CommandHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {

        // 创建一个主命令节点，所有命令都作为其子命令
        var scmCommand = Commands.literal("chocomaker").requires(src -> true);
        
        // 为帮助信息添加执行逻辑
        scmCommand.executes(ctx -> {
            ctx.getSource().sendFailure(Component.translatable("command.chocomaker.usage"));
            return 0;
        });

        if (Configs.COMMON.enableTp2p.get()) {
            scmCommand.then(
                    Commands.literal("tp2p")
                            .then(Commands.argument("target", EntityArgument.player())
                                    .executes(ctx -> {
                                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
                                        teleportToPlayer(self, target);
                                        ctx.getSource().sendSuccess(() ->
                                                Component.translatable("command.chocomaker.tp2p.success", target.getGameProfile().getName()), true);
                                        return 1;
                                    })
                            )
            );
        }

        if (Configs.COMMON.enableTpUp.get()) {
            scmCommand.then(
                    Commands.literal("tpup")
                            .executes(ctx -> {
                                ServerPlayer self = ctx.getSource().getPlayerOrException();
                                ServerLevel level = self.serverLevel();
                                BlockPos safe = findOpenSkySafePos(level, self.blockPosition());
                                if (safe == null) {
                                    safe = findSurfaceSafePos(level, self.blockPosition());
                                }
                                if (safe == null) {
                                    ctx.getSource().sendFailure(Component.translatable("command.chocomaker.tpup.no_safe_position"));
                                    return 0;
                                }
                                teleport(self, level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
                                BlockPos safePos = safe;
                                ctx.getSource().sendSuccess(
                                        () -> Component.translatable("command.chocomaker.tpup.success",
                                                safePos.getX(), safePos.getY(), safePos.getZ()
                                        ), true
                                );
                                return 1;
                            })
            );
        }
        
        // 添加夜视命令
        if (Configs.COMMON.enableNightVision.get()){
            scmCommand.then(
                Commands.literal("nv")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            if (player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
                                player.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                                ctx.getSource().sendSuccess(() ->
                                        Component.translatable("command.chocomaker.nv.disabled"), true);
                                return 1;
                            } else {
                                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                        net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                                        1440000, // 20小时
                                        0,
                                        false,
                                        false
                                ));
                                ctx.getSource().sendSuccess(() ->
                                        Component.translatable("command.chocomaker.nv.enabled"), true);
                                return 1;
                            }
                        })
            );
        }

        if (Configs.COMMON.enableUnbreakable.get()){
            scmCommand.then(
                Commands.literal("unbreakable")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            net.minecraft.world.item.ItemStack itemStack = player.getMainHandItem();

                            if (itemStack.isEmpty()) {
                                ctx.getSource().sendFailure(Component.translatable("command.chocomaker.unbreakable.no_item"));
                                return 0;
                            }

                            itemStack.setDamageValue(0);
                            itemStack.getOrCreateTag().putBoolean("Unbreakable", true);

                            ctx.getSource().sendSuccess(() ->
                                    Component.translatable("command.chocomaker.unbreakable.success"), true);
                            return 1;
                        })
            );
        }
        
        
        // 注册主命令
        event.getDispatcher().register(scmCommand);
    }


    private static void teleportToPlayer(ServerPlayer who, ServerPlayer target) {
        ServerLevel dst = target.serverLevel();
        Vec3 pos = target.position();
        who.teleportTo(dst, pos.x, pos.y, pos.z, target.getYRot(), target.getXRot());
    }

    private static void teleport(ServerPlayer who, ServerLevel level, double x, double y, double z) {
        who.teleportTo(level, x, y, z, who.getYRot(), who.getXRot());
    }

    private static BlockPos findOpenSkySafePos(ServerLevel level, BlockPos base) {
        int x = base.getX();
        int z = base.getZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 2; // 需要两格空间

        for (int y = Math.max(base.getY(), minY); y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.canSeeSkyFromBelowWater(pos)) continue;
            if (isSafeStandPos(level, pos)) {
                return pos;
            }
        }
        return null;
    }


    private static BlockPos findSurfaceSafePos(ServerLevel level, BlockPos base) {
        int x = base.getX();
        int z = base.getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int maxY = level.getMaxBuildHeight() - 2;
        for (int yy = y; yy <= maxY; yy++) {
            BlockPos pos = new BlockPos(x, yy, z);
            if (isSafeStandPos(level, pos)) {
                return pos;
            }
        }

        int minY = level.getMinBuildHeight();
        for (int yy = y; yy >= minY; yy--) {
            BlockPos pos = new BlockPos(x, yy, z);
            if (isSafeStandPos(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isSafeStandPos(ServerLevel level, BlockPos pos) {
        final var bodyState = level.getBlockState(pos);
        if (!bodyState.getCollisionShape(level, pos).isEmpty()) return false;
        if (!bodyState.getFluidState().isEmpty()) return false;

        final BlockPos headPos = pos.above();
        final var headState = level.getBlockState(headPos);
        if (!headState.getCollisionShape(level, headPos).isEmpty()) return false;
        if (!headState.getFluidState().isEmpty()) return false;

        final BlockPos feetPos = pos.below();
        final var feetState = level.getBlockState(feetPos);
        return !feetState.getCollisionShape(level, feetPos).isEmpty();
    }
}
