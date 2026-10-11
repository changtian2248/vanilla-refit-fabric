package com.btsdhz.vanillarefit.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 玻璃板“部件化”形状的公共代码。
 *
 * <p>一块玻璃板由 12 个部件拼成（都靠方块状态控制，默认都不开，外观与原版一致）：
 * 4 个水平面片（btsdhz_ne / se / nw / sw）与 8 个竖直面片（下半用原版属性，
 * 上半用本模组 btsdhz_*_up）。所有部件厚度都是 2 像素，模型与碰撞箱完全重合。
 *
 * <p>12 个属性全为 false 时保持原版形状与外观（中间那根立柱）。
 */
public final class PaneCornerSupport {

    public static final double CORNER_MIN_Y = 7.0;
    public static final double CORNER_MAX_Y = 9.0;

    private static final VoxelShape NORTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 0.0, 16.0, CORNER_MAX_Y, 8.0);
    private static final VoxelShape SOUTH_EAST =
            Block.box(8.0, CORNER_MIN_Y, 8.0, 16.0, CORNER_MAX_Y, 16.0);
    private static final VoxelShape NORTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 0.0, 8.0, CORNER_MAX_Y, 8.0);
    private static final VoxelShape SOUTH_WEST =
            Block.box(0.0, CORNER_MIN_Y, 8.0, 8.0, CORNER_MAX_Y, 16.0);

    private static final VoxelShape NORTH_LOWER = Block.box(7.0, 0.0, 0.0, 9.0, 8.0, 8.0);
    private static final VoxelShape NORTH_UPPER = Block.box(7.0, 8.0, 0.0, 9.0, 16.0, 8.0);
    private static final VoxelShape EAST_LOWER = Block.box(8.0, 0.0, 7.0, 16.0, 8.0, 9.0);
    private static final VoxelShape EAST_UPPER = Block.box(8.0, 8.0, 7.0, 16.0, 16.0, 9.0);
    private static final VoxelShape SOUTH_LOWER = Block.box(7.0, 0.0, 8.0, 9.0, 8.0, 16.0);
    private static final VoxelShape SOUTH_UPPER = Block.box(7.0, 8.0, 8.0, 9.0, 16.0, 16.0);
    private static final VoxelShape WEST_LOWER = Block.box(0.0, 0.0, 7.0, 8.0, 8.0, 9.0);
    private static final VoxelShape WEST_UPPER = Block.box(0.0, 8.0, 7.0, 8.0, 16.0, 9.0);

    private static final Map<BlockState, VoxelShape> PIECE_SHAPES = new ConcurrentHashMap<>();

    private PaneCornerSupport() {
    }

    /** 该方块状态是不是“参与部件化”的方块：原版命名空间的玻璃板、染色玻璃板与铁栏杆。 */
    public static boolean isSupportedPane(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof IronBarsBlock)) {
            return false;
        }
        return BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft");
    }

    /** 该状态是否启用了任意一个角上的水平面片。 */
    public static boolean hasCorner(BlockState state) {
        if (!isSupportedPane(state) || !state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)) {
            return false;
        }
        return state.getValue(ModBlockStateProperties.PANE_NORTH_EAST)
                || state.getValue(ModBlockStateProperties.PANE_SOUTH_EAST)
                || state.getValue(ModBlockStateProperties.PANE_NORTH_WEST)
                || state.getValue(ModBlockStateProperties.PANE_SOUTH_WEST);
    }

    /** 玻璃板最终的拾取形状 / 碰撞形状。 */
    public static VoxelShape adjustShape(VoxelShape currentShape, VoxelShape vanillaShape, BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.PANE_NORTH_EAST)) {
            return currentShape;
        }
        VoxelShape pieces = PIECE_SHAPES.computeIfAbsent(state, PaneCornerSupport::buildPieceShape);
        if (pieces.isEmpty()) {
            return currentShape;
        }
        if (currentShape != vanillaShape) {
            pieces = Shapes.or(pieces, Shapes.join(currentShape, vanillaShape, BooleanOp.ONLY_FIRST));
        }
        return pieces;
    }

    private static VoxelShape buildPieceShape(BlockState state) {
        if (!isSupportedPane(state)) {
            return Shapes.empty();
        }
        VoxelShape shape = Shapes.empty();
        if (state.getValue(CrossCollisionBlock.NORTH)) {
            shape = Shapes.or(shape, NORTH_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_NORTH_UP)) {
            shape = Shapes.or(shape, NORTH_UPPER);
        }
        if (state.getValue(CrossCollisionBlock.EAST)) {
            shape = Shapes.or(shape, EAST_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_EAST_UP)) {
            shape = Shapes.or(shape, EAST_UPPER);
        }
        if (state.getValue(CrossCollisionBlock.SOUTH)) {
            shape = Shapes.or(shape, SOUTH_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_SOUTH_UP)) {
            shape = Shapes.or(shape, SOUTH_UPPER);
        }
        if (state.getValue(CrossCollisionBlock.WEST)) {
            shape = Shapes.or(shape, WEST_LOWER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_WEST_UP)) {
            shape = Shapes.or(shape, WEST_UPPER);
        }
        if (state.getValue(ModBlockStateProperties.PANE_NORTH_EAST)) {
            shape = Shapes.or(shape, NORTH_EAST);
        }
        if (state.getValue(ModBlockStateProperties.PANE_SOUTH_EAST)) {
            shape = Shapes.or(shape, SOUTH_EAST);
        }
        if (state.getValue(ModBlockStateProperties.PANE_NORTH_WEST)) {
            shape = Shapes.or(shape, NORTH_WEST);
        }
        if (state.getValue(ModBlockStateProperties.PANE_SOUTH_WEST)) {
            shape = Shapes.or(shape, SOUTH_WEST);
        }
        return shape;
    }
}
