package com.emowolf.scm.mixin;

import com.emowolf.scm.item.DimensionalShieldItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 LivingEntity#remove(RemovalReason.KILLED) 直接调用，
 * 作为维度护盾的最后一道防线，兜底所有绕过 LivingHurtEvent / LivingDeathEvent 的致死路径。
 * <p>
 * 覆盖场景：
 * - 模组直接调用 {@code entity.remove(RemovalReason.KILLED)} 秒杀
 * - 底层 {@code Entity#setRemoved(RemovalReason.KILLED)} 绕过事件系统
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * 在 LivingEntity.remove(RemovalReason) 头部注入，
     * 当原因是 KILLED 且玩家装备了有效的维度护盾时取消移除。
     */
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void scm$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        // 仅拦截直接致死移除
        if (reason != Entity.RemovalReason.KILLED) return;

        // 仅 ServerPlayer
        if (!((Object) this instanceof ServerPlayer player)) return;

        // 维度护盾需 Curios
        if (!ModList.get().isLoaded("curios")) return;

        ItemStack shield = DimensionalShieldItem.findEquippedShield(player);
        if (shield == null) return;

        double shieldValue = DimensionalShieldItem.getShieldValue(shield);
        if (shieldValue <= 0) return;

        // 护盾有效：拦截直接致死移除
        double halved = DimensionalShieldItem.halveAndGet(shield);
        if (halved <= 0) {
            // 护盾已完全耗尽，放行死亡
            return;
        }

        ci.cancel();
        player.sendSystemMessage(Component.translatable("msg.chocomaker.dimensional_shield.instant_kill_blocked",
                DimensionalShieldItem.formatCompact(shieldValue - halved)));
    }
}
