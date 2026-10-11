package com.btsdhz.vanillarefit.client;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.btsdhz.vanillarefit.client.model.MixedSlabModel;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端模型加载插件。
 *
 * <p>对应 NeoForge 原版的 {@code SlabbedModelEvents#onModifyBakingResult}。原版用
 * {@code ModelEvent.ModifyBakingResult} 按"方块状态 → 模型"逐项包装；Fabric 的模型加载
 * 钩子只按模型 id 生效，因此这里只处理"本身就有独立模型 id"的组合半砖。
 *
 * <p>台阶/楼梯的竖形态改用静态资源实现（见本工程 {@code assets/minecraft/blockstates}
 * 与 {@code assets/btsdhz_original/models/block} 里为全部原版台阶/楼梯预生成的竖形态模型），
 * 不再需要运行时生成几何。
 */
public final class RefitModelLoadingPlugin implements ModelLoadingPlugin {

    private static final ResourceLocation MERGED_SLAB_MODEL =
            ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "block/merged_slab");

    @Override
    public void onInitializeModelLoader(Context context) {
        context.modifyModelAfterBake().register((model, ctx) -> {
            if (MERGED_SLAB_MODEL.equals(ctx.resourceId())) {
                return new MixedSlabModel(model);
            }
            return model;
        });
    }
}
