package com.btsdhz.vanillarefit.util;

import com.btsdhz.vanillarefit.block.MixedSlabBlock;
import com.btsdhz.vanillarefit.block.entity.MixedSlabBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 混合半砖的“只拆一半”逻辑。
 */
public final class MixedSlabBreakHandler {

    private MixedSlabBreakHandler() {
    }

    public static boolean tryBreakHalf(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getBlock() instanceof MixedSlabBlock) {
            return breakMixedHalf(level, pos, state, player);
        }
        if (state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            return breakDoubleHalf(level, pos, state, player);
        }
        return false;
    }

    /** 整块拆除混合半砖：移除方块并释放两块半砖的掉落物（创造模式不掉落）。 */
    public static boolean dropWholeMixed(Level level, BlockPos pos, BlockState state, Player player) {
        boolean isMixed = state.getBlock() instanceof MixedSlabBlock;
        if (!isMixed) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        Block a = Blocks.STONE_SLAB;
        Block b = Blocks.STONE_SLAB;
        if (be instanceof MixedSlabBlockEntity mixed) {
            a = mixed.getFirstSlab();
            b = mixed.getSecondSlab();
        }
        level.removeBlock(pos, false);
        if (!player.getAbilities().instabuild) {
            if (canHarvestLoose(player, a)) {
                Block.popResource(level, pos, new ItemStack(a));
            }
            if (canHarvestLoose(player, b)) {
                Block.popResource(level, pos, new ItemStack(b));
            }
        }
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    private static boolean breakMixedHalf(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MixedSlabBlockEntity mixed)) {
            return false;
        }

        boolean hitFirst = isHitFirstHalf(level, pos, state, player);
        Block hitBlock = hitFirst ? mixed.getFirstSlab() : mixed.getSecondSlab();
        Block keepBlock = hitFirst ? mixed.getSecondSlab() : mixed.getFirstSlab();

        BlockState keepState = buildKeptState(keepBlock, state, hitFirst);
        return applyBreak(level, pos, keepState, hitBlock, player);
    }

    private static boolean breakDoubleHalf(Level level, BlockPos pos, BlockState state, Player player) {
        boolean hitFirst = isHitFirstHalf(level, pos, state, player);
        Block block = state.getBlock();
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        boolean keepTop = hitFirst;
        BlockState keepState = block.defaultBlockState()
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, keepTop ? SlabType.TOP : SlabType.BOTTOM)
                .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE);
        return applyBreak(level, pos, keepState, block, player);
    }

    private static boolean applyBreak(Level level, BlockPos pos, BlockState keepState, Block hitBlock,
                                      Player player) {
        // 对称之杖：本格只拆一半，镜像那边也应该只少掉对应的那一半
        com.btsdhz.vanillarefit.event.CreateSymmetryCompat.finishHalfBreak(level, player, pos, keepState, hitBlock);
        level.setBlock(pos, keepState, 3);
        level.playSound(null, pos, hitBlock.defaultBlockState().getSoundType().getBreakSound(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!player.getAbilities().instabuild && canHarvest(player, hitBlock)) {
            Block.popResource(level, pos, new ItemStack(hitBlock));
        }
        return true;
    }

    private static boolean canHarvest(Player player, Block slabBlock) {
        BlockState state = slabBlock.defaultBlockState();
        return !state.requiresCorrectToolForDrops() || player.hasCorrectToolForDrops(state);
    }

    public static boolean canHarvestLoose(Player player, Block slabBlock) {
        return canHarvestLoose(player.getMainHandItem(), slabBlock);
    }

    /** 同上，但直接给工具物品；没有工具（null/空手）时“需要工具”的半砖不给掉落。 */
    public static boolean canHarvestLoose(ItemStack tool, Block slabBlock) {
        BlockState state = slabBlock.defaultBlockState();
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        return tool != null && !tool.isEmpty() && tool.has(DataComponents.TOOL);
    }

    /** 根据玩家视线命中位置判断是“第一半（下/北/西 半）”还是“第二半”。 */
    private static boolean isHitFirstHalf(Level level, BlockPos pos, BlockState state, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(6.0));
        BlockHitResult hit =
                level.clip(new ClipContext(
                        eye, end, ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE, player));

        if (hit.getBlockPos().equals(pos)) {
            Vec3 loc = hit.getLocation();
            double dx = loc.x - pos.getX();
            double dz = loc.z - pos.getZ();
            double dy = loc.y - pos.getY();
            VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
            return switch (mode) {
                case SLAB -> dy < 0.5;
                case VERTICAL_NS -> dz < 0.5;
                case VERTICAL_EW -> dx < 0.5;
            };
        }
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 rel = eye.subtract(center);
        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        return switch (mode) {
            case SLAB -> rel.y < 0;
            case VERTICAL_NS -> rel.z < 0;
            case VERTICAL_EW -> rel.x < 0;
        };
    }

    private static BlockState buildKeptState(Block keepBlock, BlockState mixedState, boolean hitFirst) {
        VerticalSlabMode mode = mixedState.getValue(ModBlockStateProperties.MODE);
        FluidType fluid = mixedState.getValue(ModBlockStateProperties.FLUID_TYPE);
        boolean isFirstKept = !hitFirst;

        BlockState base = keepBlock.defaultBlockState();
        if (mode == VerticalSlabMode.SLAB) {
            SlabType type = isFirstKept ? SlabType.BOTTOM : SlabType.TOP;
            return base.hasProperty(SlabBlock.TYPE) ? base.setValue(SlabBlock.TYPE, type) : base;
        }
        if (base.hasProperty(ModBlockStateProperties.MODE)) {
            return base.setValue(ModBlockStateProperties.MODE, mode)
                    .setValue(SlabBlock.TYPE, isFirstKept ? SlabType.BOTTOM : SlabType.TOP)
                    .setValue(ModBlockStateProperties.FLUID_TYPE, fluid);
        }
        return base;
    }
}
