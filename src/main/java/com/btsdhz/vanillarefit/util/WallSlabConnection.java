package com.btsdhz.vanillarefit.util;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.WallSide;

/**
 * “墙 + 台阶”的跨格连接逻辑。
 */
public final class WallSlabConnection {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private static final ThreadLocal<Boolean> RECALCULATING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> REFRESHING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private WallSlabConnection() {
    }

    public static BlockState withSlabConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        if (RECALCULATING.get() || !(state.getBlock() instanceof WallBlock)) {
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
        boolean changedWall = changedState.is(BlockTags.WALLS);
        boolean changedSlab = changedState.getBlock() instanceof SlabBlock;
        if (!changedWall && !changedSlab) {
            return;
        }
        REFRESHING.set(Boolean.TRUE);
        try {
            Set<BlockPos> candidates = new LinkedHashSet<>();
            collectSlabWalls(level, pos, candidates);
            collectSlabWalls(level, pos.above(), candidates);
            collectSlabWalls(level, pos.below(), candidates);
            for (Direction dir : HORIZONTAL) {
                collectSlabWalls(level, pos.relative(dir), candidates);
            }
            if (changedSlab) {
                candidates.add(pos.above());
                candidates.add(pos.below());
                for (Direction dir : HORIZONTAL) {
                    candidates.add(pos.relative(dir));
                }
            }
            for (BlockPos candidate : candidates) {
                refreshWall(level, candidate, changedSlab && isDirectlyAboveOrBelow(candidate, pos));
            }
        } finally {
            REFRESHING.set(Boolean.FALSE);
        }
    }

    private static boolean isDirectlyAboveOrBelow(BlockPos candidate, BlockPos pos) {
        return candidate.equals(pos.above()) || candidate.equals(pos.below());
    }

    private static void collectSlabWalls(Level level, BlockPos slabPos, Set<BlockPos> out) {
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

    private static void refreshWall(Level level, BlockPos pos, boolean updateSlabMarkers) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof WallBlock)) {
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
                if (result.getValue(wallProperty(dir)) == WallSide.NONE
                        && connectsAcrossSlab(result, level, pos, dir)) {
                    result = result.setValue(wallProperty(dir), WallSide.LOW);
                    result = recomputePost(result, level, pos);
                }
            }
            if (SlabSupport.isUnderTopSlab(result)) {
                result = fillUpUnderTopSlab(result, level, pos);
            }
            return result;
        } finally {
            RECALCULATING.set(Boolean.FALSE);
        }
    }

    private static BlockState fillUpUnderTopSlab(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        BlockState postDecision = setArmsTall(state)
                .updateShape(Direction.UP, level.getBlockState(abovePos), level, pos, abovePos);
        return setArmsTall(postDecision);
    }

    private static BlockState setArmsTall(BlockState state) {
        BlockState result = state;
        for (Direction dir : HORIZONTAL) {
            EnumProperty<WallSide> property = wallProperty(dir);
            if (result.getValue(property) != WallSide.NONE) {
                result = result.setValue(property, WallSide.TALL);
            }
        }
        return result;
    }

    private static BlockState recomputePost(BlockState state, LevelAccessor level, BlockPos pos) {
        BlockPos abovePos = pos.above();
        return state.updateShape(Direction.UP, level.getBlockState(abovePos), level, pos, abovePos);
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
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.WALLS)) {
                return true;
            }
        }
        if (SlabSupport.isUnderTopSlab(self)) {
            BlockPos slabPos = pos.above();
            if (SlabSupport.isTopSlab(level, slabPos)
                    && level.getBlockState(slabPos.relative(dir)).is(BlockTags.WALLS)) {
                return true;
            }
        }
        BlockPos neighbourPos = pos.relative(dir);
        if (SlabSupport.isBottomSlab(level, neighbourPos)) {
            BlockState above = level.getBlockState(neighbourPos.above());
            return above.is(BlockTags.WALLS) && SlabSupport.isOnSlab(above);
        }
        if (SlabSupport.isTopSlab(level, neighbourPos)) {
            BlockState below = level.getBlockState(neighbourPos.below());
            return below.is(BlockTags.WALLS) && SlabSupport.isUnderTopSlab(below);
        }
        return false;
    }

    private static EnumProperty<WallSide> wallProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> WallBlock.NORTH_WALL;
            case EAST -> WallBlock.EAST_WALL;
            case SOUTH -> WallBlock.SOUTH_WALL;
            case WEST -> WallBlock.WEST_WALL;
            default -> throw new IllegalArgumentException("不是水平方向: " + dir);
        };
    }
}
