package com.emowolf.scm.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import com.emowolf.scm.Configs;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlattenToolItem extends Item {
    private static final String TAG_START_TIME = "StartTime";
    
    public FlattenToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }

        // 仅限创造模式使用（除非配置允许生存模式）
        if (!player.isCreative() && !Configs.COMMON.flattenToolSurvivalAllowed.get()) {
            player.displayClientMessage(Component.translatable("item.chocomaker.flatten_tool.creative_only"), true);
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        CompoundTag tag = stack.getOrCreateTag();
        
        long currentTime = level.getGameTime();
        long startTime = tag.getLong(TAG_START_TIME);
        
        // 如果是第一次使用、或者间隔太久，记录开始时间
        if (!tag.contains(TAG_START_TIME) || (currentTime - startTime) > 60) { // 60 ticks = 3 second
            tag.putLong(TAG_START_TIME, currentTime);
            // 显示倒计时开始提示
            player.displayClientMessage(Component.translatable("item.chocomaker.flatten_tool.hold_to_use"), true);
            return InteractionResult.CONSUME;
        }
        
        // 检查是否已经按了足够长的时间 (> 60 ticks = 3 second)
        if ((currentTime - startTime) >= 60) {
            // 执行原有的平整逻辑
            flattenArea(context);
            
            // 重置计时器，防止连续触发
            tag.remove(TAG_START_TIME);
            return InteractionResult.CONSUME;
        }
        
        // 显示倒计时信息
        int elapsed = (int) (currentTime - startTime);
        int remaining = 60 - elapsed;
        double seconds = remaining / 20.0;
        String countdown = String.format("%.1f", seconds);
        player.displayClientMessage(
            Component.translatable("item.chocomaker.flatten_tool.countdown", countdown), true);
        return InteractionResult.CONSUME;
    }
    
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        // 仅限创造模式使用（除非配置允许生存模式）
        if (!player.isCreative() && !Configs.COMMON.flattenToolSurvivalAllowed.get()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("item.chocomaker.flatten_tool.creative_only"), true);
            }
            return InteractionResultHolder.fail(itemstack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(itemstack);
    }
    
    // 设置使用动画类型为弓箭蓄力动画
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }
    
    // 设置使用时长，影响蓄力动画的持续时间
    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000; // 很大的数值，让玩家可以一直蓄力
    }
    
    // 在使用结束时（释放右键时）执行平整操作
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }

        // 仅限创造模式使用（除非配置允许生存模式）
        if (!player.isCreative() && !Configs.COMMON.flattenToolSurvivalAllowed.get()) {
            return;
        }

        // 计算蓄力时间
        int duration = this.getUseDuration(stack) - timeLeft;
        
        // 至少需要蓄力60 ticks（3秒）才能触发
        if (duration >= 60) {
            // 触发平整操作
            flattenAreaForPlayer(level, player);
            
            // 播放声音效果
            level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                    SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
        } else if (duration > 0) {
            // 如果蓄力时间不够，显示提示信息
            player.displayClientMessage(Component.translatable("item.chocomaker.flatten_tool.hold_to_use"), true);
        }
    }
    
    // 添加物品描述
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        // 添加黄色描述文本
        tooltipComponents.add(Component.translatable("item.chocomaker.flatten_tool.desc")
                .setStyle(Style.EMPTY.withColor(TextColor.parseColor("#FFFF00"))));
    }
    
    // 重写此方法使工具在使用时不消耗耐久度
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }
    
    private void flattenAreaForPlayer(Level level, Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 获取玩家视线方向的目标位置
        BlockPos targetPos = player.blockPosition().above(); // 默认在玩家头上方
        
        // 获取基准平面高度
        int baseHeight = targetPos.getY();

        // 获取玩家所在区块坐标
        BlockPos playerPos = player.blockPosition();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        // 定义处理区域（以玩家所在区块为中心的3x3区块区域）
        int minChunkX = playerChunkX - 1;
        int maxChunkX = playerChunkX + 1;
        int minChunkZ = playerChunkZ - 1;
        int maxChunkZ = playerChunkZ + 1;

        // 计数清除的方块数量
        int removedBlocks = 0;

        // 遍历3x3区块区域内的所有方块
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // 计算当前区块的边界
                int minX = chunkX << 4;
                int maxX = minX + 15;
                int minZ = chunkZ << 4;
                int maxZ = minZ + 15;

                // 遍历当前区块内的所有XZ坐标
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        // 从世界顶部向下检查方块
                        for (int y = serverLevel.getMaxBuildHeight() - 1; y > baseHeight; y--) {
                            BlockPos pos = new BlockPos(x, y, z);
                            // 如果方块不是空气，则清除它
                            if (!serverLevel.getBlockState(pos).isAir()) {
                                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                                removedBlocks++;
                            }
                        }
                    }
                }
            }
        }

        // 向玩家发送中英双语消息
        player.sendSystemMessage(Component.translatable("item.chocomaker.flatten_tool.result",
                removedBlocks, removedBlocks));
    }
    
    private void flattenArea(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ServerLevel serverLevel = (ServerLevel) level;

        // 获取玩家点击的位置作为基准点
        BlockPos clickedPos = context.getClickedPos();
        // 获取基准平面高度（点击位置的Y坐标）
        int baseHeight = clickedPos.getY();

        // 获取点击位置所在区块坐标
        int clickedChunkX = clickedPos.getX() >> 4;
        int clickedChunkZ = clickedPos.getZ() >> 4;

        // 定义处理区域（以点击位置所在区块为中心的3x3区块区域）
        int minChunkX = clickedChunkX - 1;
        int maxChunkX = clickedChunkX + 1;
        int minChunkZ = clickedChunkZ - 1;
        int maxChunkZ = clickedChunkZ + 1;

        // 计数清除的方块数量
        int removedBlocks = 0;

        // 遍历3x3区块区域内的所有方块
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                // 计算当前区块的边界
                int minX = chunkX << 4;
                int maxX = minX + 15;
                int minZ = chunkZ << 4;
                int maxZ = minZ + 15;

                // 遍历当前区块内的所有XZ坐标
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        // 从世界顶部向下检查方块
                        for (int y = serverLevel.getMaxBuildHeight() - 1; y > baseHeight; y--) {
                            BlockPos pos = new BlockPos(x, y, z);
                            // 如果方块不是空气，则清除它
                            if (!serverLevel.getBlockState(pos).isAir()) {
                                serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                                removedBlocks++;
                            }
                        }
                    }
                }
            }
        }

        // 向玩家发送中英双语消息
        player.sendSystemMessage(Component.translatable("item.chocomaker.flatten_tool.result",
                removedBlocks, removedBlocks));
    }
}
