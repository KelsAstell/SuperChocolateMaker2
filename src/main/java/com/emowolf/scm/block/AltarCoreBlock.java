package com.emowolf.scm.block;

import com.emowolf.scm.event.RitualHandler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 祭坛核心方块 - 右键黑曜石激活祭坛后替换生成的方块
 * 拆掉时自动移除悬浮显示实体
 */
public class AltarCoreBlock extends Block {

    public AltarCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            RitualHandler.onCoreBroken(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
