package com.btsdhz.vanillarefit.event;

import com.btsdhz.vanillarefit.ModBlocks;
import com.btsdhz.vanillarefit.block.entity.MixedSlabBlockEntity;
import com.btsdhz.vanillarefit.util.MixedSlabBreakHandler;
import com.btsdhz.vanillarefit.util.FluidType;
import com.btsdhz.vanillarefit.util.ModBlockStateProperties;
import com.btsdhz.vanillarefit.util.VerticalSlabMode;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * 机械动力「对称之杖」的兼容：让组合半砖被镜像过去时也带着两块材质。
 *
 * <p>NeoForge 原版监听 {@code BlockEvent.EntityPlaceEvent} 与 {@code LevelTickEvent.Post}；
 * Fabric 侧改为：放置由本模组的 {@code BlockItemMixin} 在生成组合半砖后直接调用
 * {@link #onPlayerPlaced}，tick 收尾由 {@code BtsdhzOriginal} 在服务端 tick 事件里调用 {@link #tickLevel}。
 * 其它逻辑（破坏侧、反向探针、待写队列）与原版一致。
 *
 * <p>用反射调用机械动力的方法，不为本模组增加机械动力的编译依赖；机械动力不在、或方法改名，
 * 这里会静默关闭，只影响这一条兼容。
 */
public final class CreateSymmetryCompat {

    private static final String CREATE_MOD_ID = "create";
    private static final String WAND_ITEM_ID = "create:wand_of_symmetry";
    private static final String WAND_CLASS =
            "com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem";
    private static final String MIRROR_CLASS =
            "com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror";

    private static boolean reflectionFailed;
    private static Method getMirrorMethod;
    private static Method processMethod;

    private static final ArrayDeque<Copy> PENDING = new ArrayDeque<>();

    private record Copy(Level level, BlockPos pos, Block first, Block second) {
    }

    private static final ArrayDeque<Drop> PENDING_DROPS = new ArrayDeque<>();

    private record Drop(Level level, BlockPos pos, List<ItemStack> items) {
    }

    private static final ArrayDeque<Rebuild> PENDING_REBUILDS = new ArrayDeque<>();

    private record Rebuild(Level level, BlockPos pos, BlockState state, ItemStack item, Block hitBlock) {
    }

    private static Level breakLevel;
    private static BlockPos breakSourcePos;
    private static Object breakMirror;
    private static boolean breakIsHalf;
    private static List<BlockPos> breakTargets;
    private static List<List<ItemStack>> breakTargetItems;

    private CreateSymmetryCompat() {
    }

    /** 玩家刚用一个方块生成出组合半砖时调用（对应 NeoForge 的 EntityPlaceEvent）。 */
    public static void onPlayerPlaced(Level level, Player player, BlockPos sourcePos, BlockState sourceState) {
        if (reflectionFailed || !FabricLoader.getInstance().isModLoaded(CREATE_MOD_ID)) {
            return;
        }
        if (level == null || level.isClientSide() || player == null) {
            return;
        }
        if (sourceState.getBlock() != ModBlocks.MERGED_SLAB) {
            return;   // 只有“刚合并出来的组合半砖”需要搬材质
        }
        if (!(level.getBlockEntity(sourcePos) instanceof MixedSlabBlockEntity source)
                || isUnset(source)) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand == null || !prepareReflection()) {
            return;
        }
        Object mirror = invokeGetMirror(wand);
        if (mirror == null) {
            return;
        }
        Map<BlockPos, BlockState> targets = invokeProcess(mirror, sourcePos, sourceState);
        if (targets == null) {
            return;
        }

        Direction sourceSide = firstHalfSide(sourceState);
        for (Map.Entry<BlockPos, BlockState> entry : targets.entrySet()) {
            BlockPos targetPos = entry.getKey();
            if (targetPos.equals(sourcePos)) {
                continue;
            }
            boolean swap = mirroredFirstHalfSide(mirror, sourcePos, targetPos, sourceSide)
                    != firstHalfSide(entry.getValue());
            PENDING.add(new Copy(level, targetPos.immutable(),
                    swap ? source.getSecondSlab() : source.getFirstSlab(),
                    swap ? source.getFirstSlab() : source.getSecondSlab()));
        }
    }

    /** 服务端每 tick 结束处理待写队列（对应 NeoForge 的 LevelTickEvent.Post）。 */
    public static void tickLevel(Level level) {
        if (!PENDING.isEmpty()) {
            applyPending(level);
        }
        if (!PENDING_DROPS.isEmpty()) {
            applyPendingDrops(level);
        }
        if (!PENDING_REBUILDS.isEmpty()) {
            applyPendingRebuilds(level);
        }
    }

    private static void applyPendingRebuilds(Level tickedLevel) {
        for (Iterator<Rebuild> it = PENDING_REBUILDS.iterator(); it.hasNext(); ) {
            Rebuild rebuild = it.next();
            if (rebuild.level() != tickedLevel) {
                continue;
            }
            it.remove();
            if (!rebuild.level().getBlockState(rebuild.pos()).isAir()) {
                continue;
            }
            rebuild.level().setBlock(rebuild.pos(), rebuild.state(), 3);
            rebuild.level().playSound(null, rebuild.pos(),
                    rebuild.hitBlock().defaultBlockState().getSoundType().getBreakSound(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!rebuild.item().isEmpty()) {
                Block.popResource(rebuild.level(), rebuild.pos(), rebuild.item());
            }
        }
    }

    private static void applyPending(Level tickedLevel) {
        for (Iterator<Copy> it = PENDING.iterator(); it.hasNext(); ) {
            Copy copy = it.next();
            if (copy.level() != tickedLevel) {
                continue;
            }
            it.remove();
            Level level = copy.level();
            BlockPos pos = copy.pos();
            if (level.getBlockState(pos).getBlock() != ModBlocks.MERGED_SLAB) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof MixedSlabBlockEntity target) || !isUnset(target)) {
                continue;
            }
            target.setBlocks(copy.first(), copy.second());
        }
    }

    // ===== 破坏侧的镜像处理 =====

    public static void beginBreak(Level level, Player player, BlockPos pos, BlockState state, boolean halfBreak) {
        clearBreakContext();
        if (reflectionFailed || !FabricLoader.getInstance().isModLoaded(CREATE_MOD_ID) || level.isClientSide()) {
            return;
        }
        ItemStack wand = findWand(player);
        if (wand == null || !prepareReflection()) {
            return;
        }
        Object mirror = invokeGetMirror(wand);
        if (mirror == null) {
            return;
        }
        Map<BlockPos, BlockState> mirrored = invokeProcess(mirror, pos, state);
        if (mirrored == null) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        boolean creative = player.isCreative();
        List<BlockPos> targets = new ArrayList<>(mirrored.size());
        List<List<ItemStack>> items = new ArrayList<>(mirrored.size());
        for (BlockPos targetPos : mirrored.keySet()) {
            if (targetPos.equals(pos)) {
                continue;
            }
            BlockState targetState = level.getBlockState(targetPos);
            if (!isMirrorCounterpart(targetState, state)) {
                continue;
            }
            targets.add(targetPos.immutable());
            items.add(creative ? List.of() : collectSlabItems(level, targetPos, tool));
        }
        if (targets.isEmpty()) {
            return;
        }
        breakLevel = level;
        breakSourcePos = pos.immutable();
        breakMirror = mirror;
        breakIsHalf = halfBreak;
        breakTargets = targets;
        breakTargetItems = items;
        if (!halfBreak) {
            for (int i = 0; i < targets.size(); i++) {
                if (!items.get(i).isEmpty()) {
                    PENDING_DROPS.add(new Drop(level, targets.get(i), items.get(i)));
                }
            }
        }
    }

    public static void finishHalfBreak(Level level, Player player, BlockPos pos,
                                       BlockState keepState, Block hitBlock) {
        if (breakTargets == null || breakLevel != level || !breakIsHalf
                || breakSourcePos == null || !breakSourcePos.equals(pos)) {
            clearBreakContext();
            return;
        }
        List<BlockPos> targets = breakTargets;
        Object mirror = breakMirror;
        clearBreakContext();
        if (mirror == null) {
            return;
        }
        Direction keepSide = occupiedSide(keepState);
        BlockState probe = Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, keepSide);
        Map<BlockPos, BlockState> probeMirror = invokeProcess(mirror, pos, probe);
        if (probeMirror == null) {
            return;
        }
        Block keepBlock = keepState.getBlock();
        ItemStack removed = player.isCreative() || !MixedSlabBreakHandler.canHarvestLoose(player, hitBlock)
                ? ItemStack.EMPTY
                : new ItemStack(hitBlock);
        for (BlockPos targetPos : targets) {
            BlockState mapped = probeMirror.get(targetPos);
            Direction side = mapped != null && mapped.hasProperty(BlockStateProperties.FACING)
                    ? mapped.getValue(BlockStateProperties.FACING)
                    : keepSide;
            PENDING_REBUILDS.add(new Rebuild(level, targetPos, halfStateOf(keepBlock, side), removed, hitBlock));
        }
    }

    public static void endBreak() {
        if (breakTargets != null && breakIsHalf && breakTargetItems != null) {
            for (int i = 0; i < breakTargets.size(); i++) {
                if (!breakTargetItems.get(i).isEmpty()) {
                    PENDING_DROPS.add(new Drop(breakLevel, breakTargets.get(i), breakTargetItems.get(i)));
                }
            }
        }
        clearBreakContext();
    }

    private static void clearBreakContext() {
        breakLevel = null;
        breakSourcePos = null;
        breakMirror = null;
        breakIsHalf = false;
        breakTargets = null;
        breakTargetItems = null;
    }

    private static boolean isMirrorCounterpart(BlockState targetState, BlockState sourceState) {
        if (sourceState.getBlock() == ModBlocks.MERGED_SLAB) {
            return targetState.getBlock() == ModBlocks.MERGED_SLAB;
        }
        return targetState.getBlock() == sourceState.getBlock()
                && targetState.hasProperty(SlabBlock.TYPE)
                && targetState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
    }

    private static List<ItemStack> collectSlabItems(Level level, BlockPos pos, ItemStack tool) {
        if (!(level.getBlockEntity(pos) instanceof MixedSlabBlockEntity mixed)) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>(2);
        for (Block slab : List.of(mixed.getFirstSlab(), mixed.getSecondSlab())) {
            if (MixedSlabBreakHandler.canHarvestLoose(tool, slab)) {
                items.add(new ItemStack(slab));
            }
        }
        return items;
    }

    private static void applyPendingDrops(Level tickedLevel) {
        for (Iterator<Drop> it = PENDING_DROPS.iterator(); it.hasNext(); ) {
            Drop drop = it.next();
            if (drop.level() != tickedLevel) {
                continue;
            }
            it.remove();
            if (!drop.level().getBlockState(drop.pos()).isAir()) {
                continue;
            }
            for (ItemStack item : drop.items()) {
                Block.popResource(drop.level(), drop.pos(), item);
            }
        }
    }

    // ===== 组合半砖的“第一块（A）”在哪一侧 =====

    private static Direction firstHalfSide(BlockState state) {
        VerticalSlabMode mode = state.hasProperty(ModBlockStateProperties.MODE)
                ? state.getValue(ModBlockStateProperties.MODE)
                : VerticalSlabMode.SLAB;
        return switch (mode) {
            case VERTICAL_NS -> Direction.NORTH;
            case VERTICAL_EW -> Direction.WEST;
            default -> Direction.DOWN;
        };
    }

    private static Direction mirroredFirstHalfSide(Object mirror, BlockPos sourcePos,
                                                   BlockPos targetPos, Direction sourceSide) {
        BlockState probe = Blocks.DISPENSER.defaultBlockState()
                .setValue(BlockStateProperties.FACING, sourceSide);
        Map<BlockPos, BlockState> probeTargets = invokeProcess(mirror, sourcePos, probe);
        if (probeTargets == null) {
            return sourceSide;
        }
        BlockState mapped = probeTargets.get(targetPos);
        return mapped != null && mapped.hasProperty(BlockStateProperties.FACING)
                ? mapped.getValue(BlockStateProperties.FACING)
                : sourceSide;
    }

    private static boolean isUnset(MixedSlabBlockEntity entity) {
        return entity.getFirstSlab() == Blocks.STONE_SLAB && entity.getSecondSlab() == Blocks.STONE_SLAB;
    }

    // ===== 单半砖状态 ↔ “占住哪一侧” =====

    private static Direction occupiedSide(BlockState state) {
        VerticalSlabMode mode = state.hasProperty(ModBlockStateProperties.MODE)
                ? state.getValue(ModBlockStateProperties.MODE)
                : VerticalSlabMode.SLAB;
        boolean first = !state.hasProperty(SlabBlock.TYPE)
                || state.getValue(SlabBlock.TYPE) != SlabType.TOP;
        return switch (mode) {
            case VERTICAL_NS -> first ? Direction.NORTH : Direction.SOUTH;
            case VERTICAL_EW -> first ? Direction.WEST : Direction.EAST;
            default -> first ? Direction.DOWN : Direction.UP;
        };
    }

    private static BlockState halfStateOf(Block block, Direction side) {
        VerticalSlabMode mode = switch (side.getAxis()) {
            case Y -> VerticalSlabMode.SLAB;
            case Z -> VerticalSlabMode.VERTICAL_NS;
            default -> VerticalSlabMode.VERTICAL_EW;
        };
        boolean first = side == Direction.DOWN || side == Direction.NORTH || side == Direction.WEST;
        BlockState base = block.defaultBlockState();
        if (!base.hasProperty(ModBlockStateProperties.MODE) || !base.hasProperty(SlabBlock.TYPE)) {
            return base;
        }
        BlockState state = base
                .setValue(ModBlockStateProperties.MODE, mode)
                .setValue(SlabBlock.TYPE, first ? SlabType.BOTTOM : SlabType.TOP);
        if (state.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
            state = state.setValue(ModBlockStateProperties.FLUID_TYPE, FluidType.NONE);
        }
        return state;
    }

    // ===== 机械动力那边的取用（全程反射） =====

    @Nullable
    private static ItemStack findWand(Player player) {
        int size = player.getInventory().getSelectionSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()
                    && WAND_ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                return stack;
            }
        }
        return null;
    }

    private static boolean prepareReflection() {
        if (getMirrorMethod != null && processMethod != null) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            Class<?> wandClass = Class.forName(WAND_CLASS);
            Class<?> mirrorClass = Class.forName(MIRROR_CLASS);
            getMirrorMethod = wandClass.getMethod("getMirror", ItemStack.class);
            processMethod = mirrorClass.getMethod("process", BlockPos.class, BlockState.class);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return false;
        }
    }

    @Nullable
    private static Object invokeGetMirror(ItemStack wand) {
        try {
            return getMirrorMethod.invoke(null, wand);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Map<BlockPos, BlockState> invokeProcess(Object mirror, BlockPos pos, BlockState state) {
        try {
            return (Map<BlockPos, BlockState>) processMethod.invoke(mirror, pos, state);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }
}
