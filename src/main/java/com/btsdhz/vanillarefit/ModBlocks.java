package com.btsdhz.vanillarefit;

import com.btsdhz.vanillarefit.block.MixedSlabBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** 方块与物品注册（NeoForge 的 DeferredRegister 对应 Fabric 的 Registry.register）。 */
public final class ModBlocks {

    public static final Block SMOOTH_STONE_STAIRS = registerBlock("smooth_stone_stairs",
            new StairBlock(Blocks.SMOOTH_STONE.defaultBlockState(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE)));

    public static final Item SMOOTH_STONE_STAIRS_ITEM = registerItem("smooth_stone_stairs",
            new BlockItem(SMOOTH_STONE_STAIRS, new Item.Properties()));

    // 混合半砖：一格子里放两块不同材质的半砖（用方块实体存材质）
    public static final Block MERGED_SLAB = registerBlock("merged_slab",
            // 不复制石头的 requiresCorrectToolForDrops（默认 false），
            // 使混合半砖任何工具都能采集掉落两块半砖，避免整挖无掉落。
            new MixedSlabBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(1.5F, 6.0F)
                            .sound(SoundType.STONE)));

    public static final Item MERGED_SLAB_ITEM = registerItem("merged_slab",
            new BlockItem(MERGED_SLAB, new Item.Properties()));

    private ModBlocks() {
    }

    private static Block registerBlock(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, name), block);
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, name), item);
    }

    /** 触发类加载（静态字段即完成注册）。 */
    public static void register() {
    }
}
