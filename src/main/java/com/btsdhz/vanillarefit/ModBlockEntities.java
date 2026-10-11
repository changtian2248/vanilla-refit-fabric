package com.btsdhz.vanillarefit;

import com.btsdhz.vanillarefit.block.entity.MixedSlabBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** 方块实体注册。 */
public final class ModBlockEntities {

    public static final BlockEntityType<MixedSlabBlockEntity> MIXED_SLAB = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "mixed_slab"),
            BlockEntityType.Builder.of(MixedSlabBlockEntity::create, ModBlocks.MERGED_SLAB).build(null));

    private ModBlockEntities() {
    }

    public static void register() {
    }
}
