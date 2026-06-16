package com.emowolf.scm.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 传送护符饰品：存储目标玩家信息，按快捷键传送到目标玩家
 * 右键点击其他玩家实体以绑定目标
 */
public class TeleportCharmItem extends Item implements ICurioItem {

    public static final String TAG_TARGET_UUID = "TargetUUID";
    public static final String TAG_TARGET_NAME = "TargetName";

    public TeleportCharmItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /**
     * 右键点击玩家实体时绑定目标
     */
    public static InteractionResult bindToPlayer(ItemStack stack, Player user, Player target) {
        if (user.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_TARGET_UUID, target.getUUID());
        tag.putString(TAG_TARGET_NAME, target.getGameProfile().getName());

        user.sendSystemMessage(Component.translatable("item.chocomaker.teleport_charm.bound", target.getGameProfile().getName()));
        return InteractionResult.SUCCESS;
    }

    /**
     * 获取饰品上绑定的目标玩家UUID
     */
    @Nullable
    public static UUID getTargetUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_TARGET_UUID)) {
            return tag.getUUID(TAG_TARGET_UUID);
        }
        return null;
    }

    /**
     * 获取饰品上绑定的目标玩家名称
     */
    @Nullable
    public static String getTargetName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_TARGET_NAME)) {
            return tag.getString(TAG_TARGET_NAME);
        }
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents.add(Component.translatable("item.chocomaker.teleport_charm.desc"));

        String targetName = getTargetName(stack);
        if (targetName != null) {
            tooltipComponents.add(Component.translatable("item.chocomaker.teleport_charm.target", targetName));
        } else {
            tooltipComponents.add(Component.translatable("item.chocomaker.teleport_charm.no_target"));
        }
    }
}
