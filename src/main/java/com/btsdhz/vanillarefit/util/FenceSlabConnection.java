package com.btsdhz.vanillarefit.util;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * “栅栏 + 台阶”的跨格连接逻辑，与墙那一侧的 {@link WallSlabConnection} 一一对应。
 */
public final class FenceSlabConnection {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final ThreadLocal<Boolean> RECALCULATING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> REFRESHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FenceSlabConnection() {
    }

    public static BlockState withSlabConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        if (RECALCULATING.get() || !(state.getBlock() instanceof FenceBlock)) {
            return state;
        }
        if (!isSlabRelated(state, level, pos)) {
            return state;
        }
        return recomputeConnections(state, level, pos);
    }

    public static void refreshAround(Level level, BlockPos pos, BlockState changedState) {
        if (REFRESHING.get()) {
            return;
        }
        boolean changedFence = changedState.getBlock() instanceof FenceBlock;
        boolean changedSlab = changedState.getBlock() instanceof SlabBlock;
        if (!changedFence && !changedSlab) {
            return;
        }
        REFRESHING.set(Boolean.TRUE);
        try {
            Set<BlockPos> candidates = new LinkedHashSet<>();
            collectSlabFences(level, pos, candidates);
            collectSlabFences(level, pos.above(), candidates);
            collectSlabFences(level, pos.below(), candidates);
            for (Direction dir : HORIZONTAL) {
                collectSlabFences(level, pos.relative(dir), candidates);
            }
            if (changedSlab) {
                candidates.add(pos.above());
                candidates.add(pos.below());
                for (Direction dir : HORIZONTAL) {
                    candidates.add(pos.relative(dir));
                }
            }
            for (BlockPos candidate : candidates) {
                refreshFence(level, candidate, changedSlab && isDirectlyAboveOrBelow(candidate, pos));
            }
        } finally {
            REFRESHING.set(Boolean.FALSE);
        }
    }

    private static boolean isDirectlyAboveOrBelow(BlockPos candidate, BlockPos pos) {
        return candidate.equals(pos.above()) || candidate.equals(pos.below());
    }

    private static void collectSlabFences(Level level, BlockPos slabPos, Set<BlockPos> out) {
        if (SlabSupport.isBottomSlab(level, slabPos)) {
            out.add(slabPos.above());
        } else if (SlabSupport.isTopSlab(level, slabPos)) {
            out.add(slabPos.below());
        } else {
            return;
        }
        for (Direction dir : HORIZONTAL) {
            out.add(slabPos.relative(dir));
        }
    }

    private static void refreshFence(Level level, BlockPos pos, boolean updateSlabMarkers) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceBlock)) {
            return;
        }
        BlockState updated = state;
        if (updateSlabMarkers && updated.hasProperty(ModBlockStateProperties.SLAB_OFFSET)) {
            updated = updated.setValue(ModBlockStateProperties.SLAB_OFFSET,
                    SlabSupport.offsetAt(level, pos));
        }
        updated = recomputeConnections(updated, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    private static BlockState recomputeConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        RECALCULATING.set(Boolean.TRUE);
        try {
            BlockState result = state;
            for (Direction dir : HORIZONTAL) {
                BlockPos neighbourPos = pos.relative(dir);
                result = result.updateShape(dir, level.getBlockState(neighbourPos), level, pos, neighbourPos);
            }
            for (Direction dir : HORIZONTAL) {
                if (!result.getValue(fenceProperty(dir)) && connectsAcrossSlab(result, level, pos, dir)) {
                    result = result.setValue(fenceProperty(dir), true);
                }
            }
            return result;
        } finally {
            RECALCULATING.set(Boolean.FALSE);
        }
    }

    private static boolean isSlabRelated(BlockState state, LevelAccessor level, BlockPos pos) {
        if (SlabSupport.isOnSlab(state) || SlabSupport.isUnderTopSlab(state)) {
            return true;
        }
        for (Direction dir : HORIZONTAL) {
            BlockPos neighbourPos = pos.relative(dir);
            if (SlabSupport.isBottomSlab(level, neighbourPos) || SlabSupport.isTopSlab(level, neighbourPos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean connectsAcrossSlab(BlockState self, LevelAccessor level, BlockPos pos, Direction dir) {
        if (SlabSupport.isOnSlab(self)) {
            BlockPos slabPos = pos.below();
            if (SlabSupport.isBottomSlab(level, slabPos)
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.FENCES)) {
                return true;
            }
        }
        if (SlabSupport.isUnderTopSlab(self)) {
            BlockPos slabPos = pos.above();
            if (SlabSupport.isTopSlab(level, slabPos)
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.FENCES)) {
                return true;
            }
        }
        BlockPos neighbourPos = pos.relative(dir);
        if (SlabSupport.isBottomSlab(level, neighbourPos)) {
            BlockState above = level.getBlockState(neighbourPos.above());
            return above.is(BlockTags.FENCES) && SlabSupport.isOnSlab(above);
        }
        if (SlabSupport.isTopSlab(level, neighbourPos)) {
            BlockState below = level.getBlockState(neighbourPos.below());
            return below.is(BlockTags.FENCES) && SlabSupport.isUnderTopSlab(below);
        }
        return false;
    }

    private static BooleanProperty fenceProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case WEST -> CrossCollisionBlock.WEST;
            default -> throw new IllegalArgumentException("不是水平方向: " + dir);
        };
    }
}
