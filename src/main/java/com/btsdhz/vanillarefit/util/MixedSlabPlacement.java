package com.btsdhz.vanillarefit.util;

import com.btsdhz.vanillarefit.ModBlocks;
import com.btsdhz.vanillarefit.block.entity.MixedSlabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 统一的“半砖放置/填充”判定：只要点击面朝向某个半砖的空余半砖空间，就在该格填入半砖。
 */
public final class MixedSlabPlacement {

    private MixedSlabPlacement() {
    }

    public static boolean tryFill(Level level, BlockPos clickedPos, BlockState targetState, Block block,
                                  Direction clickedFace) {
        return tryFill(level, clickedPos, targetState, block, clickedFace, false);
    }

    public static boolean tryFill(Level level, BlockPos clickedPos, BlockState targetState, Block block,
                                  Direction clickedFace, boolean mixedOnly) {
        // 情形 A：点击半砖本体，点击面朝向它的空余半砖空间
        if (isDirectFillable(targetState, clickedFace)) {
            return fill(level, clickedPos, targetState, block, mixedOnly);
        }
        // 情形 B：点击相邻方块、且该面朝向一个有空余半砖空间的半砖格
        BlockPos neighborPos = clickedPos.relative(clickedFace);
        if (!neighborPos.equals(clickedPos)) {
            BlockState sideState = level.getBlockState(neighborPos);
            if (isNeighborFillable(sideState)) {
                return fill(level, neighborPos, sideState, block, mixedOnly);
            }
        }
        return false;
    }

    private static boolean isDirectFillable(BlockState state, Direction clickedFace) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.hasProperty(ModBlockStateProperties.MODE)) {
            return false;
        }
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.DOUBLE) {
            return false;
        }
        int existing = orientationOf(state);
        return existing >= 0 && clickedFace == emptyFaceOf(existing);
    }

    private static boolean isNeighborFillable(BlockState state) {
        if (!(state.getBlock() instanceof SlabBlock) || !state.hasProperty(ModBlockStateProperties.MODE)) {
            return false;
        }
        return state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE && orientationOf(state) >= 0;
    }

    private static boolean fill(Level level, BlockPos pos, BlockState targetState, Block block, boolean mixedOnly) {
        int existing = orientationOf(targetState);
        if (existing < 0) {
            return false;
        }
        boolean sameMaterial = targetState.getBlock() == block;
        if (sameMaterial) {
            if (mixedOnly) {
                return false;
            }
            BlockState doubleState = targetState.setValue(SlabBlock.TYPE, SlabType.DOUBLE)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, false);
            if (level.setBlock(pos, doubleState, 3)) {
                level.playSound(null, pos, doubleState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                return true;
            }
            return false;
        }

        int newOrientation = oppositeOf(existing);
        Block a;
        Block b;
        if (existing < newOrientation) {
            a = targetState.getBlock();
            b = block;
        } else {
            a = block;
            b = targetState.getBlock();
        }
        ParticleOrientation o1 = orientation(existing);
        BlockState mergedState = buildMergedState(o1.mode(), o1.type(), a, b, targetState, level, pos);
        if (level.setBlock(pos, mergedState, 3)) {
            if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed) {
                mixed.setBlocks(a, b);
            }
            level.playSound(null, pos, mergedState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

    private static Direction emptyFaceOf(int code) {
        return switch (code) {
            case 0 -> Direction.UP;
            case 1 -> Direction.DOWN;
            case 2 -> Direction.SOUTH;
            case 3 -> Direction.NORTH;
            case 4 -> Direction.EAST;
            default -> Direction.WEST;
        };
    }

    private static int oppositeOf(int code) {
        return switch (code) {
            case 0 -> 1;
            case 1 -> 0;
            case 2 -> 3;
            case 3 -> 2;
            case 4 -> 5;
            default -> 4;
        };
    }

    private static BlockState buildMergedState(VerticalSlabMode mode, SlabType type, Block a, Block b,
                                               BlockState target, Level level, BlockPos pos) {
        FluidType fluid = FluidType.NONE;
        return ModBlocks.MERGED_SLAB.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, type)
                .setValue(ModBlockStateProperties.FLUID_TYPE, fluid);
    }

    private static ParticleOrientation orientation(int code) {
        return switch (code) {
            case 0 -> new ParticleOrientation(VerticalSlabMode.SLAB, SlabType.BOTTOM);
            case 1 -> new ParticleOrientation(VerticalSlabMode.SLAB, SlabType.TOP);
            case 2 -> new ParticleOrientation(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM);
            case 3 -> new ParticleOrientation(VerticalSlabMode.VERTICAL_NS, SlabType.TOP);
            case 4 -> new ParticleOrientation(VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM);
            default -> new ParticleOrientation(VerticalSlabMode.VERTICAL_EW, SlabType.TOP);
        };
    }

    private static int orientationOf(BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.MODE)) {
            return -1;
        }
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        SlabType type = state.hasProperty(SlabBlock.TYPE) ? state.getValue(SlabBlock.TYPE) : SlabType.BOTTOM;
        return switch (mode) {
            case SLAB -> type == SlabType.BOTTOM ? 0 : 1;
            case VERTICAL_NS -> type == SlabType.BOTTOM ? 2 : 3;
            case VERTICAL_EW -> type == SlabType.BOTTOM ? 4 : 5;
        };
    }

    private record ParticleOrientation(VerticalSlabMode mode, SlabType type) {
    }
}
