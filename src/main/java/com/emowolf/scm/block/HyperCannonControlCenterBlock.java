package com.emowolf.scm.block;

import com.emowolf.scm.blockentity.HyperCannonControlCenterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class HyperCannonControlCenterBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public HyperCannonControlCenterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HyperCannonControlCenterBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HyperCannonControlCenterBlockEntity cannonBE && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, cannonBE, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                net.minecraft.world.level.block.Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide()) return;

        // Redstone trigger: fire on rising edge
        if (level.hasNeighborSignal(pos)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HyperCannonControlCenterBlockEntity cannonBE) {
                // Find the nearest player to attribute the kill
                Player nearestPlayer = level.getNearestPlayer(pos.getX() + 0.5,
                        pos.getY() + 0.5, pos.getZ() + 0.5, 32.0, false);
                if (nearestPlayer != null && level instanceof ServerLevel serverLevel) {
                    cannonBE.fire(serverLevel, nearestPlayer);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        if (!level.isClientSide()) {
            return (lvl, pos, st, be) -> {
                if (be instanceof HyperCannonControlCenterBlockEntity cannonBE) {
                    HyperCannonControlCenterBlockEntity.serverTick((ServerLevel) lvl, pos, st, cannonBE);
                }
            };
        }
        return null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HyperCannonControlCenterBlockEntity cannonBE && level instanceof ServerLevel serverLevel) {
                cannonBE.dropContents(serverLevel);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
