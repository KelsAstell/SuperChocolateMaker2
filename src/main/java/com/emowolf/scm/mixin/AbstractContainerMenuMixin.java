package com.emowolf.scm.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修改 AbstractContainerMenu#stillValid 的距离判定逻辑：
 * 将硬编码的 64.0（8 格）替换为玩家实际的 BLOCK_REACH 属性值，
 * 使距离护符（ReachCharm）对方块和容器交互的加成对所有容器（原版+模组）生效。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    /**
     * 在 stillValid 方法头部注入，用动态距离判定替代硬编码 64.0 并取消原逻辑。
     * 取 max(64.0, reach²) 确保无护符时保持原版 8 格行为。
     */
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private static void scm$stillValid(ContainerLevelAccess access, Player player, Block block,
                                        CallbackInfoReturnable<Boolean> cir) {
        double reach = player.getBlockReach();
        boolean result = access.evaluate((level, pos) ->
                level.getBlockState(pos).is(block) &&
                        player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                                <= Math.max(64.0, reach * reach),
                true);
        cir.setReturnValue(result);
    }
}
