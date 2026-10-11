package com.btsdhz.vanillarefit.block;

import com.btsdhz.vanillarefit.ModBlockEntities;
import com.btsdhz.vanillarefit.block.entity.MixedSlabBlockEntity;
import com.btsdhz.vanillarefit.util.FluidType;
import com.btsdhz.vanillarefit.util.MixedSlabBreakHandler;
import com.btsdhz.vanillarefit.util.ModBlockStateProperties;
import com.btsdhz.vanillarefit.util.VerticalSlabMode;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 混合半砖：一格子里放两块“不同材质”的半砖。
 *
 * <p>复用现有属性体系：MODE（SLAB / VERTICAL_NS / VERTICAL_EW）+ TYPE（哪一半）。
 * 方块实体存 slabA/slabB 两块半砖的材质。
 */
public class MixedSlabBlock extends Block implements EntityBlock {
    public static final MapCodec<MixedSlabBlock> CODEC = simpleCodec(MixedSlabBlock::new);

    /** 中键拾取时用于判断“点中哪一半”的缓存玩家（客户端每 tick 更新；服务端为 null）。 */
    public static Player cachedPlayer;

    public MixedSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(ModBlockStateProperties.MODE, VerticalSlabMode.SLAB)
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.BOTTOM)
                .setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ModBlockStateProperties.MODE);
        builder.add(net.minecraft.world.level.block.SlabBlock.TYPE);
        builder.add(ModBlockStateProperties.FLUID_TYPE);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MixedSlabBlockEntity mixed) {
            Block target = isHitFirstHalf(level, pos, state, player)
                    ? mixed.getFirstSlab()
                    : mixed.getSecondSlab();
            return target.defaultBlockState().getDestroyProgress(player, level, pos);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MixedSlabBlockEntity(ModBlockEntities.MIXED_SLAB, pos, state);
    }

    /** 中键拾取：返回“准星命中的那半”的半砖物品。 */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed) {
            Block hit = mixed.getFirstSlab();
            if (level.isClientSide() && cachedPlayer != null) {
                boolean first = isHitFirstHalf(level, pos, state, cachedPlayer);
                hit = first ? mixed.getFirstSlab() : mixed.getSecondSlab();
            }
            return new ItemStack(hit);
        }
        return super.getCloneItemStack(level, pos, state);
    }

    private static boolean isHitFirstHalf(BlockGetter level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof Level l) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            Vec3 end = eye.add(look.scale(6.0));
            BlockHitResult hit = l.clip(new ClipContext(
                    eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
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
        }
        return true;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof MixedSlabBlockEntity mixed) {
            if (!player.getAbilities().instabuild) {
                Block first = mixed.getFirstSlab();
                Block second = mixed.getSecondSlab();
                if (MixedSlabBreakHandler.canHarvestLoose(player, first)) {
                    popResource(level, pos, new ItemStack(first));
                }
                if (MixedSlabBreakHandler.canHarvestLoose(player, second)) {
                    popResource(level, pos, new ItemStack(second));
                }
            }
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MixedSlabBlockEntity mixed) {
            ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
            List<ItemStack> drops = new ArrayList<>(2);
            for (Block slab : List.of(mixed.getFirstSlab(), mixed.getSecondSlab())) {
                if (MixedSlabBreakHandler.canHarvestLoose(tool, slab)) {
                    drops.add(new ItemStack(slab));
                }
            }
            return drops;
        }
        return super.getDrops(state, params);
    }
}
