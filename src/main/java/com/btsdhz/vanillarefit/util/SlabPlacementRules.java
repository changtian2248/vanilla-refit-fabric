package com.btsdhz.vanillarefit.util;

import com.btsdhz.vanillarefit.config.BtsdhzConfig;
import com.btsdhz.vanillarefit.config.Blocklist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * 台阶“竖放还是平放”的判定，放置逻辑（BlockItemMixin）与状态生成
 * （SlabBlockMixin.getStateForPlacement）共用，保证两边结论一致。
 */
public final class SlabPlacementRules {

    private SlabPlacementRules() {
    }

    /** 这次点击是否应该放竖半砖。 */
    public static boolean wantsVertical(UseOnContext context, PlacementMode mode, Block block) {
        if (block != null && !Blocklist.allows(block)) {
            return false;
        }
        if (mode == PlacementMode.VANILLA || mode == PlacementMode.MOD_3) {
            return false;
        }
        Direction face = context.getClickedFace();
        if (face != Direction.UP && face != Direction.DOWN) {
            return false;
        }
        return mode != PlacementMode.MOD_2 || !isInCenterSquare(context);
    }

    /** 逻辑 3 里，某个点击面对应的“紧贴该面”的半砖朝向。 */
    public static Orientation flushAgainst(Direction face) {
        return switch (face) {
            case UP -> new Orientation(VerticalSlabMode.SLAB, SlabType.BOTTOM);
            case DOWN -> new Orientation(VerticalSlabMode.SLAB, SlabType.TOP);
            case NORTH -> new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.TOP);
            case SOUTH -> new Orientation(VerticalSlabMode.VERTICAL_NS, SlabType.BOTTOM);
            case WEST -> new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.TOP);
            case EAST -> new Orientation(VerticalSlabMode.VERTICAL_EW, SlabType.BOTTOM);
        };
    }

    /** 半砖朝向：形态（平放 / 竖南北 / 竖东西）+ 占哪一半。 */
    public record Orientation(VerticalSlabMode mode, SlabType type) {
    }

    /** 点击位置是否落在被点方块面的中央正方形内。 */
    public static boolean isInCenterSquare(UseOnContext context) {
        double ratio = centerSquareRatio();
        if (ratio <= 0.0D) {
            return false;
        }
        BlockPos facePos = context.getClickedPos();
        Vec3 loc = context.getClickLocation();
        double fx = loc.x - facePos.getX();
        double fz = loc.z - facePos.getZ();
        double half = ratio / 2.0D;
        return Math.abs(fx - 0.5D) <= half && Math.abs(fz - 0.5D) <= half;
    }

    /** 配置里的中央正方形边长比例；读取失败时按默认 0.5 处理。 */
    public static double centerSquareRatio() {
        try {
            return BtsdhzConfig.SLAB_CENTER_SQUARE_RATIO.get();
        } catch (RuntimeException exception) {
            return 0.5D;
        }
    }
}
