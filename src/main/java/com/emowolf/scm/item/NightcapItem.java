package com.emowolf.scm.item;

import com.emowolf.scm.event.SleepCapHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 睡帽：右键使用后让玩家立刻睡着；在下界或末地使用则引起爆炸。
 */
public class NightcapItem extends Item {

    private static final float EXPLOSION_POWER = 4.0f;

    public NightcapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;

        // 检查是否在下界或末地
        if (level.dimension() == Level.NETHER || level.dimension() == Level.END) {
            // 引起爆炸
            level.explode(null, player.getX(), player.getY() + 1.0, player.getZ(),
                    EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            return InteractionResultHolder.consume(stack);
        }

        // 让玩家立刻睡着
        // 使用SleepCapHandler标记玩家为强制睡眠状态
        // 这会在NBT中添加标记，并通过tick事件持续维持睡眠姿态，绕过白天/床的检查
        SleepCapHandler.markForcedSleep(serverPlayer);

        if (!player.isCreative()) {
            stack.shrink(1);
        }

        return InteractionResultHolder.consume(stack);
    }

    /**
     * 左键点击生物时触发（在攻击伤害应用之前调用）：
     * 取消伤害事件，消耗睡帽，让目标生物立刻睡着。
     * 在下界或末地使用时同样会引起爆炸。
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (player.level().isClientSide()) {
            return true; // 客户端也取消攻击动画
        }

        if (!(entity instanceof LivingEntity target)) {
            return false;
        }

        Level level = entity.level();

        // 使用SleepCapHandler标记生物为强制睡眠状态
        // 这会在NBT中添加标记，并通过tick事件持续维持睡眠姿态
        SleepCapHandler.markForcedSleep(target);

        if (!player.isCreative()) {
            stack.shrink(1);
        }

        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("item.chocomaker.nightcap.desc"));
    }
}
