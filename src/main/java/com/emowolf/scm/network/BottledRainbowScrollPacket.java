package com.emowolf.scm.network;

import com.emowolf.scm.item.BottledRainbowItem;
import com.emowolf.scm.item.SCMItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：瓶装彩虹颜色切换（滚轮触发）
 */
public class BottledRainbowScrollPacket {
    private final int delta; // +1 或 -1

    public BottledRainbowScrollPacket(int delta) {
        this.delta = delta;
    }

    public static BottledRainbowScrollPacket decode(FriendlyByteBuf buf) {
        return new BottledRainbowScrollPacket(buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(delta);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;

        ctx.get().enqueueWork(() -> {
            ItemStack stack = player.getMainHandItem();
            if (stack.is(SCMItems.BOTTLED_RAINBOW.get())) {
                BottledRainbowItem.cycleColor(stack, delta);
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
