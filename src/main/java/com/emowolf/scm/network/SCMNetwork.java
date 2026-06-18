package com.emowolf.scm.network;

import com.emowolf.scm.SCM;
import com.emowolf.scm.blockentity.EternalBeaconBlockEntity;
import com.emowolf.scm.blockentity.HyperCannonControlCenterBlockEntity;
import com.emowolf.scm.item.TeleportCharmItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraftforge.network.PacketDistributor;

public class SCMNetwork {
    public static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SCM.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.messageBuilder(SetBeaconLevelPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetBeaconLevelPacket::encode)
                .decoder(SetBeaconLevelPacket::decode)
                .consumerMainThread(SetBeaconLevelPacket::handle)
                .add();

        CHANNEL.messageBuilder(TeleportCharmPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TeleportCharmPacket::encode)
                .decoder(TeleportCharmPacket::decode)
                .consumerMainThread(TeleportCharmPacket::handle)
                .add();

        CHANNEL.messageBuilder(HyperCannonFirePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HyperCannonFirePacket::encode)
                .decoder(HyperCannonFirePacket::decode)
                .consumerMainThread(HyperCannonFirePacket::handle)
                .add();

        CHANNEL.messageBuilder(HyperCannonCoordPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HyperCannonCoordPacket::encode)
                .decoder(HyperCannonCoordPacket::decode)
                .consumerMainThread(HyperCannonCoordPacket::handle)
                .add();

        CHANNEL.messageBuilder(BottledRainbowScrollPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BottledRainbowScrollPacket::encode)
                .decoder(BottledRainbowScrollPacket::decode)
                .consumerMainThread(BottledRainbowScrollPacket::handle)
                .add();

        CHANNEL.messageBuilder(RitualAnimationPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RitualAnimationPacket::encode)
                .decoder(RitualAnimationPacket::decode)
                .consumerMainThread(RitualAnimationPacket::handle)
                .add();

        CHANNEL.messageBuilder(LightSpearBeamPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LightSpearBeamPacket::encode)
                .decoder(LightSpearBeamPacket::decode)
                .consumerMainThread(LightSpearBeamPacket::handle)
                .add();

        CHANNEL.messageBuilder(RacketSwingPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RacketSwingPacket::encode)
                .decoder(RacketSwingPacket::decode)
                .consumerMainThread(RacketSwingPacket::handle)
                .add();

        CHANNEL.messageBuilder(ChocolateMixerMixPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChocolateMixerMixPacket::encode)
                .decoder(ChocolateMixerMixPacket::decode)
                .consumerMainThread(ChocolateMixerMixPacket::handle)
                .add();

        CHANNEL.messageBuilder(MachineGunFirePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MachineGunFirePacket::encode)
                .decoder(MachineGunFirePacket::decode)
                .consumerMainThread(MachineGunFirePacket::handle)
                .add();
    }

    public static class SetBeaconLevelPacket {
        private final BlockPos pos;
        private final int level;

        public SetBeaconLevelPacket(BlockPos pos, int level) {
            this.pos = pos;
            this.level = level;
        }

        public static SetBeaconLevelPacket decode(FriendlyByteBuf buf) {
            return new SetBeaconLevelPacket(buf.readBlockPos(), buf.readInt());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeInt(level);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof EternalBeaconBlockEntity beaconBE) {
                // Validate distance
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    beaconBE.setSelectedLevel(level);
                }
            }
        }
    }

    /**
     * 客户端发送：请求传送到传送护符绑定的目标玩家
     */
    public static class TeleportCharmPacket {
        // 空包，服务端从玩家Curios饰品槽位读取目标信息

        public TeleportCharmPacket() {
        }

        public static TeleportCharmPacket decode(FriendlyByteBuf buf) {
            return new TeleportCharmPacket();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ctx.get().enqueueWork(() -> {
                // 从Curios饰品栏查找传送护符
                var result = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                        .map(handler -> handler.findFirstCurio(SCMItems.TELEPORT_CHARM.get()))
                        .orElse(java.util.Optional.empty());

                if (result.isEmpty()) return;

                var slotResult = result.get();
                UUID targetUUID = TeleportCharmItem.getTargetUUID(slotResult.stack());
                if (targetUUID == null) {
                    player.sendSystemMessage(Component.translatable("item.chocomaker.teleport_charm.no_target"));
                    return;
                }

                ServerPlayer target = player.server.getPlayerList().getPlayer(targetUUID);
                if (target == null) {
                    player.sendSystemMessage(Component.translatable("item.chocomaker.teleport_charm.target_offline"));
                    return;
                }

                // 传送到目标玩家
                player.teleportTo(target.serverLevel(),
                        target.getX(), target.getY(), target.getZ(),
                        target.getYRot(), target.getXRot());
                player.sendSystemMessage(Component.translatable(
                        "command.chocomaker.tp2p.success", target.getGameProfile().getName()));
            });

            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 客户端发送：实时更新天基炮目标坐标（爆炸蓄力中修改坐标）
     */
    public static class HyperCannonCoordPacket {
        private final BlockPos pos;
        private final int targetX;
        private final int targetY;
        private final int targetZ;

        public HyperCannonCoordPacket(BlockPos pos, int targetX, int targetY, int targetZ) {
            this.pos = pos;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }

        public static HyperCannonCoordPacket decode(FriendlyByteBuf buf) {
            return new HyperCannonCoordPacket(
                    buf.readBlockPos(), buf.readInt(), buf.readInt(), buf.readInt()
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeInt(targetX);
            buf.writeInt(targetY);
            buf.writeInt(targetZ);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof HyperCannonControlCenterBlockEntity cannonBE) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    cannonBE.setTargetX(targetX);
                    cannonBE.setTargetY(targetY);
                    cannonBE.setTargetZ(targetZ);
                }
            }
        }
    }

    /**
     * 客户端发送：天基炮控制中心开火请求
     */
    public static class HyperCannonFirePacket {
        private final BlockPos pos;
        private final int targetX;
        private final int targetY;
        private final int targetZ;
        private final int mode;

        public HyperCannonFirePacket(BlockPos pos, int targetX, int targetY, int targetZ, int mode) {
            this.pos = pos;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.mode = mode;
        }

        public static HyperCannonFirePacket decode(FriendlyByteBuf buf) {
            return new HyperCannonFirePacket(
                    buf.readBlockPos(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()
            );
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeInt(targetX);
            buf.writeInt(targetY);
            buf.writeInt(targetZ);
            buf.writeInt(mode);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof HyperCannonControlCenterBlockEntity cannonBE) {
                // Validate distance
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    // Update target coordinates and mode from packet
                    cannonBE.setTargetX(targetX);
                    cannonBE.setTargetY(targetY);
                    cannonBE.setTargetZ(targetZ);
                    cannonBE.setMode(mode);
                    // Fire!
                    cannonBE.fire((net.minecraft.server.level.ServerLevel) player.level(), player);
                }
            }
        }
    }

    /**
     * 服务端 -> 客户端：触发献祭仪式相机动画
     */
    public static class RitualAnimationPacket {
        private final BlockPos altarPos;

        public RitualAnimationPacket(BlockPos altarPos) {
            this.altarPos = altarPos;
        }

        public static RitualAnimationPacket decode(FriendlyByteBuf buf) {
            return new RitualAnimationPacket(buf.readBlockPos());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(altarPos);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.emowolf.scm.client.camera.RitualAnimationManager.startAnimation(altarPos));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 服务端 -> 客户端：在目标坐标显示红色信标光束（光矛模式发射特效）
     */
    public static class LightSpearBeamPacket {
        private final int targetX;
        private final int targetZ;
        private final int duration;

        public LightSpearBeamPacket(int targetX, int targetZ, int duration) {
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.duration = duration;
        }

        public static LightSpearBeamPacket decode(FriendlyByteBuf buf) {
            return new LightSpearBeamPacket(buf.readInt(), buf.readInt(), buf.readInt());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeInt(targetX);
            buf.writeInt(targetZ);
            buf.writeInt(duration);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.emowolf.scm.client.LightSpearBeamManager.addBeam(targetX, targetZ, duration));
            });
            ctx.get().setPacketHandled(true);
        }

        /** 向所有玩家广播光束特效 */
        public static void sendToAll(int targetX, int targetZ, int duration) {
            CHANNEL.send(PacketDistributor.ALL.noArg(), new LightSpearBeamPacket(targetX, targetZ, duration));
        }
    }

    /**
     * 客户端 → 服务端：球拍左键挥拍请求
     */
    public static class RacketSwingPacket {

        public RacketSwingPacket() {
        }

        public static RacketSwingPacket decode(FriendlyByteBuf buf) {
            return new RacketSwingPacket();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ctx.get().enqueueWork(() -> {
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof com.emowolf.scm.item.RacketItem)) return;

                long energy = com.emowolf.scm.item.RacketItem.getEnergy(stack);
                if (energy < com.emowolf.scm.item.RacketItem.COST_PER_SWING) {
                    player.sendSystemMessage(Component.translatable("msg.chocomaker.racket.no_energy"));
                    return;
                }

                int count = com.emowolf.scm.item.RacketItem.deflectProjectiles(player.level(), player);
                if (count > 0) {
                    com.emowolf.scm.item.RacketItem.addEnergy(stack, -com.emowolf.scm.item.RacketItem.COST_PER_SWING);
                    player.sendSystemMessage(Component.translatable("msg.chocomaker.racket.deflected", count,
                            com.emowolf.scm.item.RacketItem.getEnergy(stack)));
                }
            });

            ctx.get().setPacketHandled(true);
        }
    }

    /**
     * 客户端 → 服务端：巧克力混合机请求混合
     */
    public static class ChocolateMixerMixPacket {
        private final BlockPos pos;

        public ChocolateMixerMixPacket(BlockPos pos) {
            this.pos = pos;
        }

        public static ChocolateMixerMixPacket decode(FriendlyByteBuf buf) {
            return new ChocolateMixerMixPacket(buf.readBlockPos());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (be instanceof com.emowolf.scm.blockentity.ChocolateMixerBlockEntity mixerBE) {
                if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    mixerBE.tryMix();
                }
            }
        }
    }

    /**
     * 客户端发送：请求发射可可机枪穿透光束（左键空气时）
     */
    public static class MachineGunFirePacket {
        public MachineGunFirePacket() {
        }

        public static MachineGunFirePacket decode(FriendlyByteBuf buf) {
            return new MachineGunFirePacket();
        }

        public void encode(FriendlyByteBuf buf) {
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof com.emowolf.scm.item.MachineGunItem)) return;

            ctx.get().enqueueWork(() -> {
                long beamCharge = com.emowolf.scm.item.MachineGunItem.getBeamCharge(stack);
                if (beamCharge < com.emowolf.scm.item.MachineGunItem.MAX_BEAM_CHARGE) return;

                com.emowolf.scm.item.MachineGunItem.fireBeam(player.level(), player, beamCharge, true);
                com.emowolf.scm.item.MachineGunItem.setBeamCharge(stack, 0);
                com.emowolf.scm.item.MachineGunItem.playBeamSound(player.level(), player);
            });
        }
    }
}
