package com.emowolf.scm.event;

import com.emowolf.scm.Configs;
import com.emowolf.scm.SCM;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SCM.MODID)
public class InfiniteFluidHandler {

    /**
     * 缓存仪式解锁状态，避免每次方块事件都读取 SavedData。
     * 缓存按维度 key + game time 每 100 tick（5秒）刷新一次。
     */
    private static boolean cachedUnlocked = false;
    private static long cacheExpiry = 0;

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof Level world)) return;
        if (world.isClientSide()) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        checkInfiniteLiquid(world, pos, state);
    }

    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide()) return;
        Level world = (Level) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = world.getBlockState(pos);
        checkInfiniteLiquid(world, pos, state);
    }

    private static void checkInfiniteLiquid(Level world, BlockPos pos, BlockState state) {
        boolean isWater = state.is(Blocks.WATER) && Configs.COMMON.enableInfiniteWater.get();
        boolean isLava  = state.is(Blocks.LAVA)  && Configs.COMMON.enableInfiniteLava.get();
        if (!isWater && !isLava) return;

        // 使用缓存检查仪式解锁状态，避免高频 SavedData I/O
        if (!isUnlocked(world)) return;

        int sourceCount = 0;
        for (BlockPos offset : new BlockPos[]{pos.north(), pos.south(), pos.east(), pos.west()}) {
            BlockState neighbor = world.getBlockState(offset);
            if (neighbor.is(state.getBlock())
                    && neighbor.getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL) == 0) {
                sourceCount++;
            }
        }

        if (sourceCount >= 2) {
            world.setBlockAndUpdate(pos, state.getBlock().defaultBlockState());
        }
    }

    private static boolean isUnlocked(Level world) {
        if (!(world instanceof ServerLevel serverLevel)) return false;
        long now = world.getGameTime();
        if (now >= cacheExpiry) {
            cachedUnlocked = RitualUnlockData.get(serverLevel).isInfiniteFluidUnlocked();
            cacheExpiry = now + 100; // 每 5 秒刷新
        }
        return cachedUnlocked;
    }
}
