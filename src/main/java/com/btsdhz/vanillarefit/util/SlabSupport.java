package com.btsdhz.vanillarefit.util;

import com.btsdhz.vanillarefit.config.BtsdhzConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 判断方块是否为“普通水平下半台阶”的工具。
 *
 * <p>原版 canSupportCenter 使用 SupportType.CENTER，只把“中心列实心”的方块视为可承载，
 * 因此普通下半台阶（中心列是空的）无法承载火把/灯笼，需要本模组额外放行。
 * 这里的“下半台阶”指：普通水平放置（MODE=SLAB）、且 TYPE=BOTTOM 的台阶；
 * 竖台阶、双台阶都不算，含水的下半台阶仍然算（FLUID_TYPE 不影响）。
 */
public final class SlabSupport {

    private SlabSupport() {
    }

    /** @return 该台阶方块是否属于本模组支持的范围。 */
    public static boolean isSupportedSlab(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        if (namespace.equals("minecraft") || namespace.equals("btsdhz_original")) {
            return true;
        }
        return allowModdedSlabs();
    }

    /** @return 该楼梯方块是否属于本模组支持的范围。 */
    public static boolean isSupportedStair(Block block) {
        String namespace = BuiltInRegistries.BLOCK.getKey(block).getNamespace();
        if (namespace.equals("minecraft") || namespace.equals("btsdhz_original")) {
            return true;
        }
        return allowModdedStairs();
    }

    private static boolean allowModdedSlabs() {
        try {
            return BtsdhzConfig.ALLOW_MODDED_SLABS.get();
        } catch (RuntimeException notLoadedYet) {
            return false;
        }
    }

    private static boolean allowModdedStairs() {
        try {
            return BtsdhzConfig.ALLOW_MODDED_STAIRS.get();
        } catch (RuntimeException notLoadedYet) {
            return false;
        }
    }

    /** @return pos 处是否为普通水平下半台阶 */
    public static boolean isBottomSlab(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && isSupportedSlab(state.getBlock())
                && state.hasProperty(ModBlockStateProperties.MODE)
                && state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    /** @return pos 处是否为普通水平上半台阶 */
    public static boolean isTopSlab(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && isSupportedSlab(state.getBlock())
                && state.hasProperty(ModBlockStateProperties.MODE)
                && state.getValue(ModBlockStateProperties.MODE) == VerticalSlabMode.SLAB
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }

    /** @return 该状态的“贴台阶”情况。 */
    public static SlabOffset offset(BlockState state) {
        if (state.hasProperty(ModBlockStateProperties.SLAB_OFFSET)) {
            return state.getValue(ModBlockStateProperties.SLAB_OFFSET);
        }
        if (state.hasProperty(ModBlockStateProperties.ON_SLAB) && state.getValue(ModBlockStateProperties.ON_SLAB)) {
            return SlabOffset.LOWERED;
        }
        return SlabOffset.NONE;
    }

    /** @return 该方块状态是否为“整体下移了半格的方块”。 */
    public static boolean isOnSlab(BlockState state) {
        return offset(state) == SlabOffset.LOWERED;
    }

    /** @return 该方块状态是否为“整体上移了半格的方块”。 */
    public static boolean isUnderTopSlab(BlockState state) {
        return offset(state) == SlabOffset.RAISED;
    }

    /** @return pos 处的方块是否被本模组整体下移了半格。 */
    public static boolean isLoweredBlock(BlockGetter level, BlockPos pos) {
        return isOnSlab(level.getBlockState(pos));
    }

    /** @return pos 处的方块是否被本模组整体上移了半格。 */
    public static boolean isRaisedBlock(BlockGetter level, BlockPos pos) {
        return isUnderTopSlab(level.getBlockState(pos));
    }

    /** @return 该位置的方块"贴台阶"情况：下方是下台阶 → LOWERED；否则上方是上台阶 → RAISED。 */
    public static SlabOffset offsetAt(BlockGetter level, BlockPos pos) {
        if (isBottomSlab(level, pos.below())) {
            return SlabOffset.LOWERED;
        }
        return isTopSlab(level, pos.above()) ? SlabOffset.RAISED : SlabOffset.NONE;
    }

    /** @return 本格是下移半格的方块，且正上方也是下移半格的方块。 */
    public static boolean hasLoweredBlockAbove(BlockGetter level, BlockPos pos) {
        return isOnSlab(level.getBlockState(pos)) && isOnSlab(level.getBlockState(pos.above()));
    }

    /** @return 本格是上移半格的方块，且正下方也是上移半格的方块。 */
    public static boolean hasRaisedBlockBelow(BlockGetter level, BlockPos pos) {
        return isUnderTopSlab(level.getBlockState(pos)) && isUnderTopSlab(level.getBlockState(pos.below()));
    }

    /** 叠在本格上方/下方、且同样位移了半格的方块。 */
    public record StackedNeighbour(BlockState state, VoxelShape shape, VoxelShape collision) {
    }

    /** @return 需要并入本格的邻居方块；没有这种关系时返回 null。 */
    public static StackedNeighbour stackedNeighbour(BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockState self = level.getBlockState(pos);
        if (isOnSlab(self)) {
            BlockPos abovePos = pos.above();
            BlockState above = level.getBlockState(abovePos);
            if (isOnSlab(above)) {
                return new StackedNeighbour(above,
                        fromAboveShape(above, above.getShape(level, abovePos, context)),
                        fromAboveShape(above, above.getCollisionShape(level, abovePos, context)));
            }
        }
        if (isUnderTopSlab(self)) {
            BlockPos belowPos = pos.below();
            BlockState below = level.getBlockState(belowPos);
            if (isUnderTopSlab(below)) {
                return new StackedNeighbour(below,
                        fromBelowShape(below, below.getShape(level, belowPos, context)),
                        fromBelowShape(below, below.getCollisionShape(level, belowPos, context)));
            }
        }
        return null;
    }

    /** @return 该方块是否是“竖着放”的火把类。 */
    public static boolean isStandingTorch(Block block) {
        return (block instanceof TorchBlock && !(block instanceof WallTorchBlock))
                || (block instanceof RedstoneTorchBlock && !(block instanceof RedstoneWallTorchBlock));
    }

    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> DOWN_SHIFTED_SHAPES = new ConcurrentHashMap<>();
    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> UP_SHIFTED_SHAPES = new ConcurrentHashMap<>();

    /** 把方块形状整体下移半格（8/16 单位）。 */
    public static VoxelShape shiftDownHalf(BlockState state, VoxelShape shape) {
        return movedOnce(DOWN_SHIFTED_SHAPES, state, shape, -0.5);
    }

    /** 把方块形状整体上移半格（8/16 单位）。 */
    public static VoxelShape shiftUpHalf(BlockState state, VoxelShape shape) {
        return movedOnce(UP_SHIFTED_SHAPES, state, shape, 0.5);
    }

    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> FROM_ABOVE_SHAPES = new ConcurrentHashMap<>();
    private static final Map<BlockState, Map<VoxelShape, VoxelShape>> FROM_BELOW_SHAPES = new ConcurrentHashMap<>();
    private static final Map<VoxelShape, Map<VoxelShape, VoxelShape>> MERGED_SHAPES = new ConcurrentHashMap<>();
    private static final int MERGED_HOST_LIMIT = 4096;
    private static final int SHAPE_FLAVOR_LIMIT = 8;

    /** 把“正上方格子”的方块形状换算到本格坐标（整体上移一格）。 */
    public static VoxelShape fromAboveShape(BlockState state, VoxelShape shape) {
        return movedOnce(FROM_ABOVE_SHAPES, state, shape, 1.0);
    }

    /** 把“正下方格子”的方块形状换算到本格坐标（整体下移一格）。 */
    public static VoxelShape fromBelowShape(BlockState state, VoxelShape shape) {
        return movedOnce(FROM_BELOW_SHAPES, state, shape, -1.0);
    }

    private static VoxelShape movedOnce(Map<BlockState, Map<VoxelShape, VoxelShape>> cache,
                                        BlockState state, VoxelShape shape, double dy) {
        Map<VoxelShape, VoxelShape> bySource = cache.computeIfAbsent(state, key -> new ConcurrentHashMap<>());
        VoxelShape cached = bySource.get(shape);
        if (cached != null) {
            return cached;
        }
        if (bySource.size() >= SHAPE_FLAVOR_LIMIT) {
            bySource.clear();
        }
        VoxelShape moved = shape.move(0.0, dy, 0.0);
        bySource.put(shape, moved);
        return moved;
    }

    /** 把邻居伸进本格的那部分形状并入本格形状。 */
    public static VoxelShape mergedWithNeighbour(VoxelShape hostShape, VoxelShape neighbourShape) {
        if (MERGED_SHAPES.size() >= MERGED_HOST_LIMIT) {
            MERGED_SHAPES.clear();
        }
        Map<VoxelShape, VoxelShape> byNeighbour =
                MERGED_SHAPES.computeIfAbsent(hostShape, key -> new ConcurrentHashMap<>());
        VoxelShape cached = byNeighbour.get(neighbourShape);
        if (cached != null) {
            return cached;
        }
        if (byNeighbour.size() >= SHAPE_FLAVOR_LIMIT) {
            byNeighbour.clear();
        }
        VoxelShape merged = Shapes.or(hostShape, neighbourShape);
        byNeighbour.put(neighbourShape, merged);
        return merged;
    }

    /** @return pos 上方的方块是否为"整体下移了半格的方块"。 */
    public static boolean isLoweredOnSlabAbove(BlockGetter level, BlockPos pos) {
        return isLoweredBlock(level, pos.above());
    }

    /** @return pos 下方的方块是否为"整体上移了半格的方块"。 */
    public static boolean isRaisedUnderTopSlabBelow(BlockGetter level, BlockPos pos) {
        return isRaisedBlock(level, pos.below());
    }

    /** @return pos 处的方块是否为含熔岩的台阶（FLUID_TYPE == LAVA）。 */
    public static boolean isLavaSlab(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(ModBlockStateProperties.FLUID_TYPE)
                && state.getValue(ModBlockStateProperties.FLUID_TYPE) == FluidType.LAVA;
    }
}
