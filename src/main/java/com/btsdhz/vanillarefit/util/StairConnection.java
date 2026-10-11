package com.btsdhz.vanillarefit.util;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.jetbrains.annotations.NotNull;

/**
 * 竖楼梯的连接形态。
 *
 * <p>连接形态**借用原版 {@code shape} 的 5 个值当载体**：竖放时 shape 没有原版语义。
 * 载体对应关系：{@code straight} → 无连接、{@code inner_left} → 连接左、
 * {@code inner_right} → 连接右、{@code outer_left} → 拐角、{@code outer_right} → 双面连接。
 */
public enum StairConnection implements StringRepresentable {
    NONE("none"),                // 普通竖 L
    CORNER("corner"),            // 拐角连接：补块在上半还是下半由 half 决定
    CONN_RIGHT("conn_right"),    // 单大面连接右（模型A）
    CONN_LEFT("conn_left"),      // 单大面连接左（模型B）
    CONN_DOUBLE("conn_double");  // 双大面连接（模型C）

    private final String name;

    StairConnection(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    /** 该形态占用的 {@code shape} 载体值。 */
    public StairsShape toShape() {
        return switch (this) {
            case NONE -> StairsShape.STRAIGHT;
            case CONN_LEFT -> StairsShape.INNER_LEFT;
            case CONN_RIGHT -> StairsShape.INNER_RIGHT;
            case CORNER -> StairsShape.OUTER_LEFT;
            case CONN_DOUBLE -> StairsShape.OUTER_RIGHT;
        };
    }

    /** 载体值 → 形态。 */
    public static StairConnection fromShape(StairsShape shape) {
        return switch (shape) {
            case STRAIGHT -> NONE;
            case INNER_LEFT -> CONN_LEFT;
            case INNER_RIGHT -> CONN_RIGHT;
            case OUTER_LEFT -> CORNER;
            case OUTER_RIGHT -> CONN_DOUBLE;
        };
    }

    /** 读一个楼梯状态的连接形态（先看 {@code btsdhz_vertical}：不是竖放就一律 NONE）。 */
    public static StairConnection of(BlockState state) {
        if (!state.hasProperty(ModBlockStateProperties.VERTICAL)
                || !state.hasProperty(BlockStateProperties.STAIRS_SHAPE)
                || !state.getValue(ModBlockStateProperties.VERTICAL)) {
            return NONE;
        }
        return fromShape(state.getValue(BlockStateProperties.STAIRS_SHAPE));
    }
}
