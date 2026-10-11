package com.btsdhz.vanillarefit;

import com.btsdhz.vanillarefit.entity.FenceKnotEntity;
import com.btsdhz.vanillarefit.entity.SitEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** 实体注册（NeoForge 的 DeferredRegister 对应 Fabric 的 Registry.register）。 */
public final class ModEntities {

    public static final EntityType<FenceKnotEntity> FENCE_KNOT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "fence_knot"),
            EntityType.Builder.<FenceKnotEntity>of(FenceKnotEntity::new, MobCategory.MISC)
                    .sized(0.375F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("fence_knot"));

    public static final EntityType<SitEntity> SIT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "sit"),
            EntityType.Builder.<SitEntity>of(SitEntity::new, MobCategory.MISC)
                    .sized(0.001F, 0.001F)
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("sit"));

    private ModEntities() {
    }

    /** 触发类加载（静态字段即完成注册）。 */
    public static void register() {
    }
}
