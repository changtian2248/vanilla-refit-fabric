package com.btsdhz.vanillarefit.util;

import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

public class ModBlockStateProperties {
    public static final EnumProperty<VerticalSlabMode> MODE =
            EnumProperty.create("btsdhz_mode", VerticalSlabMode.class);

    public static final EnumProperty<FluidType> FLUID_TYPE =
            EnumProperty.create("btsdhz_fluid", FluidType.class);

    // 竖楼梯标记：false=原版平放楼梯，true=竖楼梯。
    //
    // 必须用默认 false 的属性：原版 BooleanProperty 的取值顺序是 [true, false]，
    // 而"缺属性"会按第一个取值解析。中途加入模组读取旧存档时，存档里没有 btsdhz_vertical，
    // 用 BooleanProperty 就会把原本的平放楼梯全部解析成竖楼梯（形态凭空改变）。
    public static final Property<Boolean> VERTICAL =
            new DefaultFalseBooleanProperty("btsdhz_vertical");

    // 火把/灵魂火把（含红石火把）是否放在"下台阶"（下半台阶）上。
    public static final Property<Boolean> ON_SLAB =
            new DefaultFalseBooleanProperty("btsdhz_on_slab");

    // 墙/栅栏/灯笼与台阶的贴合情况：不贴 / 贴下台阶（下移半格）/ 贴上台阶下（上移半格）。
    public static final EnumProperty<SlabOffset> SLAB_OFFSET =
            EnumProperty.create("btsdhz_slab_offset", SlabOffset.class);

    // 玻璃板四角水平面片。
    public static final Property<Boolean> PANE_NORTH_EAST =
            new DefaultFalseBooleanProperty("btsdhz_ne");

    public static final Property<Boolean> PANE_SOUTH_EAST =
            new DefaultFalseBooleanProperty("btsdhz_se");

    public static final Property<Boolean> PANE_NORTH_WEST =
            new DefaultFalseBooleanProperty("btsdhz_nw");

    public static final Property<Boolean> PANE_SOUTH_WEST =
            new DefaultFalseBooleanProperty("btsdhz_sw");

    // 玻璃板四个方向的上半部分。
    public static final Property<Boolean> PANE_NORTH_UP =
            new DefaultFalseBooleanProperty("btsdhz_north_up");

    public static final Property<Boolean> PANE_EAST_UP =
            new DefaultFalseBooleanProperty("btsdhz_east_up");

    public static final Property<Boolean> PANE_SOUTH_UP =
            new DefaultFalseBooleanProperty("btsdhz_south_up");

    public static final Property<Boolean> PANE_WEST_UP =
            new DefaultFalseBooleanProperty("btsdhz_west_up");
}
