package com.btsdhz.vanillarefit.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

/**
 * 竖楼梯「状态 → 模型 + 旋转」的唯一映射表。
 */
public final class StairShapeModels {

    /** 需要的模型种类；具体用哪个 ModelFile 由调用方（datagen / 运行时模型）决定。 */
    public enum ModelKind {
        VERTICAL,
        VANILLA_INNER,
        CONN_RIGHT,
        CONN_LEFT,
        CONN_DOUBLE,
        FLAT_STRAIGHT,
        FLAT_INNER,
        FLAT_OUTER
    }

    public record Shape(ModelKind kind, int xRot, int yRot, boolean uvLock, boolean topVariant) {}

    /** 四个水平朝向的遍历顺序（与 blockstate 里 facing 的顺序一致）。 */
    public static final Direction[] HORIZONTAL_FACINGS =
            {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final int[] FACING_Y = {270, 0, 90, 180};

    private static final StairsShape[] FLAT_SHAPES = {
            StairsShape.STRAIGHT, StairsShape.INNER_LEFT, StairsShape.INNER_RIGHT,
            StairsShape.OUTER_LEFT, StairsShape.OUTER_RIGHT
    };

    private static final int[][] FLAT_Y_BOTTOM = {
            {270, 0, 90, 180},
            {180, 270, 0, 90},
            {270, 0, 90, 180},
            {180, 270, 0, 90},
            {270, 0, 90, 180}
    };

    private static final int[][] FLAT_Y_TOP = {
            {270, 0, 90, 180},
            {270, 0, 90, 180},
            {0, 90, 180, 270},
            {270, 0, 90, 180},
            {0, 90, 180, 270}
    };

    private static final boolean[][] FLAT_UV_BOTTOM = {
            {true, false, true, true},
            {true, true, false, true},
            {true, false, true, true},
            {true, true, false, true},
            {true, false, true, true}
    };

    private StairShapeModels() {
    }

    public static int flatShapeCount() {
        return FLAT_SHAPES.length;
    }

    public static StairsShape flatShape(int index) {
        return FLAT_SHAPES[index];
    }

    /** 竖放：连接形态 × 朝向 × 上下 → 模型与旋转。 */
    public static Shape vertical(StairConnection connection, Half half, int facingIndex) {
        int y = FACING_Y[facingIndex];
        boolean top = half == Half.TOP;
        return switch (connection) {
            case NONE -> new Shape(ModelKind.VERTICAL, 0, y, true, false);
            case CORNER -> half == Half.BOTTOM
                    ? new Shape(ModelKind.VANILLA_INNER, 0, (y + 270) % 360, true, false)
                    : new Shape(ModelKind.VANILLA_INNER, 180, y, true, false);
            case CONN_RIGHT -> new Shape(ModelKind.CONN_RIGHT, 0, y, true, top);
            case CONN_LEFT -> new Shape(ModelKind.CONN_LEFT, 0, y, true, top);
            case CONN_DOUBLE -> new Shape(ModelKind.CONN_DOUBLE, 0, y, true, top);
        };
    }

    /** 平放：原版形状 × 朝向 × 上下 → 模型与旋转。 */
    public static Shape flat(int shapeIndex, Half half, int facingIndex) {
        boolean top = half == Half.TOP;
        int y = top ? FLAT_Y_TOP[shapeIndex][facingIndex] : FLAT_Y_BOTTOM[shapeIndex][facingIndex];
        boolean uvLock = top || FLAT_UV_BOTTOM[shapeIndex][facingIndex];
        ModelKind kind = switch (shapeIndex) {
            case 0 -> ModelKind.FLAT_STRAIGHT;
            case 1, 2 -> ModelKind.FLAT_INNER;
            default -> ModelKind.FLAT_OUTER;
        };
        return new Shape(kind, top ? 180 : 0, y, uvLock, false);
    }
}
