package com.btsdhz.vanillarefit.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.function.UnaryOperator;

/**
 * 给本模组注入的“方向性属性”补上原版 {@code BlockState#mirror} / {@code BlockState#rotate} 语义。
 */
public final class SymmetrySupport {

    private static final Direction[] HORIZONTALS =
            { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    private SymmetrySupport() {
    }

    /** 原版 {@code BlockState#mirror} 的结果再补上本模组属性的镜像。 */
    public static BlockState mirror(BlockState original, BlockState mirrored, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return mirrored;
        }
        BlockState result = mirrored;

        Direction slabSide = verticalSlabSide(original);
        if (slabSide != null) {
            result = withVerticalSlabSide(result, mirror.mirror(slabSide));
        }

        if (isVerticalStair(original)) {
            result = result
                    .setValue(StairBlock.FACING,
                            mirroredStairFacing(original.getValue(StairBlock.FACING), mirror))
                    .setValue(StairBlock.SHAPE,
                            mirroredConnection(StairConnection.of(original)).toShape());
        }
        result = transformPane(result, original, mirror::mirror);
        return result;
    }

    /** 原版 {@code BlockState#rotate} 的结果再补上本模组属性的旋转。 */
    public static BlockState rotate(BlockState original, BlockState rotated, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return rotated;
        }
        Direction slabSide = verticalSlabSide(original);
        BlockState result = rotated;
        if (slabSide != null) {
            result = withVerticalSlabSide(result, rotation.rotate(slabSide));
        }
        return transformPane(result, original, rotation::rotate);
    }

    private static BlockState transformPane(BlockState result, BlockState original,
                                            UnaryOperator<Direction> move) {
        if (!original.hasProperty(ModBlockStateProperties.PANE_NORTH_UP)) {
            return result;
        }
        BlockState out = result;
        for (Direction dir : HORIZONTALS) {
            Property<Boolean> to = paneUpProperty(move.apply(dir));
            Property<Boolean> from = paneUpProperty(dir);
            if (to != null && from != null) {
                out = out.setValue(to, original.getValue(from));
            }
        }
        out = out.setValue(paneCornerProperty(move.apply(Direction.NORTH), move.apply(Direction.EAST)),
                original.getValue(ModBlockStateProperties.PANE_NORTH_EAST));
        out = out.setValue(paneCornerProperty(move.apply(Direction.NORTH), move.apply(Direction.WEST)),
                original.getValue(ModBlockStateProperties.PANE_NORTH_WEST));
        out = out.setValue(paneCornerProperty(move.apply(Direction.SOUTH), move.apply(Direction.EAST)),
                original.getValue(ModBlockStateProperties.PANE_SOUTH_EAST));
        out = out.setValue(paneCornerProperty(move.apply(Direction.SOUTH), move.apply(Direction.WEST)),
                original.getValue(ModBlockStateProperties.PANE_SOUTH_WEST));
        return out;
    }

    private static Property<Boolean> paneUpProperty(Direction dir) {
        return switch (dir) {
            case NORTH -> ModBlockStateProperties.PANE_NORTH_UP;
            case EAST -> ModBlockStateProperties.PANE_EAST_UP;
            case SOUTH -> ModBlockStateProperties.PANE_SOUTH_UP;
            case WEST -> ModBlockStateProperties.PANE_WEST_UP;
            default -> null;
        };
    }

    private static Property<Boolean> paneCornerProperty(Direction a, Direction b) {
        boolean north = a == Direction.NORTH || b == Direction.NORTH;
        boolean south = a == Direction.SOUTH || b == Direction.SOUTH;
        boolean east = a == Direction.EAST || b == Direction.EAST;
        boolean west = a == Direction.WEST || b == Direction.WEST;
        if (north && east) {
            return ModBlockStateProperties.PANE_NORTH_EAST;
        }
        if (north && west) {
            return ModBlockStateProperties.PANE_NORTH_WEST;
        }
        if (south && east) {
            return ModBlockStateProperties.PANE_SOUTH_EAST;
        }
        if (south && west) {
            return ModBlockStateProperties.PANE_SOUTH_WEST;
        }
        return ModBlockStateProperties.PANE_NORTH_EAST;
    }

    private static Direction verticalSlabSide(BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.MODE)
                || !state.hasProperty(SlabBlock.TYPE)) {
            return null;
        }
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        if (mode == VerticalSlabMode.SLAB) {
            return null;
        }
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.DOUBLE) {
            return null;
        }
        boolean bottom = type == SlabType.BOTTOM;
        if (mode == VerticalSlabMode.VERTICAL_NS) {
            return bottom ? Direction.NORTH : Direction.SOUTH;
        }
        return bottom ? Direction.WEST : Direction.EAST;
    }

    private static BlockState withVerticalSlabSide(BlockState state, Direction side) {
        if (side.getAxis() == Direction.Axis.Y) {
            return state;
        }
        VerticalSlabMode mode = side.getAxis() == Direction.Axis.Z
                ? VerticalSlabMode.VERTICAL_NS
                : VerticalSlabMode.VERTICAL_EW;
        SlabType type = side == Direction.NORTH || side == Direction.WEST
                ? SlabType.BOTTOM
                : SlabType.TOP;
        return state
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, type);
    }

    private static boolean isVerticalStair(BlockState state) {
        return state.hasProperty(ModBlockStateProperties.VERTICAL)
                && state.hasProperty(StairBlock.FACING)
                && state.hasProperty(StairBlock.SHAPE)
                && state.getValue(ModBlockStateProperties.VERTICAL);
    }

    private static Direction mirroredStairFacing(Direction facing, Mirror mirror) {
        return switch (mirror) {
            case FRONT_BACK -> switch (facing) {
                case NORTH -> Direction.EAST;
                case EAST -> Direction.NORTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.SOUTH;
                default -> facing;
            };
            case LEFT_RIGHT -> switch (facing) {
                case NORTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.EAST;
                default -> facing;
            };
            default -> facing;
        };
    }

    private static StairConnection mirroredConnection(StairConnection conn) {
        return switch (conn) {
            case CONN_LEFT -> StairConnection.CONN_RIGHT;
            case CONN_RIGHT -> StairConnection.CONN_LEFT;
            default -> conn;
        };
    }
}
