package com.btsdhz.vanillarefit.util;

import java.util.List;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * 台阶/楼梯的放置逻辑。
 *
 * <p>台阶和楼梯各存一份、互不影响（可以只把台阶切回原版而楼梯保持本模组逻辑）。
 */
public enum PlacementMode implements StringRepresentable {
    /** 本模组逻辑 1：竖放/混合台阶等（见 BlockItemMixin、SlabBlockMixin、StairBlockMixin）。 */
    MOD("mod"),
    /** 本模组逻辑 2（目前只有台阶）：中央正方形内放置"平放台阶"，正方形之外竖放。 */
    MOD_2("mod2"),
    /** 本模组逻辑 3（目前只有台阶）：紧贴点击的那个面放置。不需要辅助线。 */
    MOD_3("mod3"),
    /** 原版逻辑：完全走原版放置。 */
    VANILLA("vanilla");

    /** 台阶可选的模式顺序（按切换键轮转）。 */
    public static final List<PlacementMode> SLAB_ORDER = List.of(MOD, MOD_2, MOD_3, VANILLA);
    /** 楼梯目前还没有逻辑 2。 */
    public static final List<PlacementMode> STAIR_ORDER = List.of(MOD, VANILLA);

    private final String name;

    PlacementMode(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    /** 按切换键轮转。 */
    public PlacementMode next(boolean forStairs) {
        List<PlacementMode> order = forStairs ? STAIR_ORDER : SLAB_ORDER;
        int index = order.indexOf(this);
        return order.get((index + 1) % order.size());
    }

    public static PlacementMode byOrdinal(int ordinal) {
        PlacementMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : MOD;
    }
}
