package com.btsdhz.vanillarefit.client.model;

import com.btsdhz.vanillarefit.util.ModBlockStateProperties;
import com.btsdhz.vanillarefit.util.VerticalSlabMode;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachedBlockView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 组合半砖的渲染：读取方块实体暴露的渲染数据（两块材质），把两块半砖各自的模型
 * 分别摆到它占的那一半里。
 *
 * <p>对应 NeoForge 原版的 {@code MixedSlabModel}（用 {@code ModelProperty}/{@code ModelData}）；
 * Fabric 侧改用渲染数据附件 {@link RenderAttachedBlockView#getBlockEntityRenderAttachment(BlockPos)}。
 *
 * <p>摆位用纯几何变换：平放时把其中一块整体上移 0.5；竖放时把平放模型绕轴旋 90° 再平移，
 * 使半砖立起来贴到对应的那一侧。
 */
public class MixedSlabModel extends ForwardingBakedModel {

    public MixedSlabModel(BakedModel originalModel) {
        this.wrapped = originalModel;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos,
                               Supplier<RandomSource> randomSupplier, RenderContext context) {
        Object data = null;
        if (blockView instanceof RenderAttachedBlockView view && pos != null) {
            data = view.getBlockEntityRenderAttachment(pos);
        }
        if (!(data instanceof Block[] blocks) || blocks.length < 2
                || !state.hasProperty(ModBlockStateProperties.MODE)) {
            super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
            return;
        }

        VerticalSlabMode mode = state.getValue(ModBlockStateProperties.MODE);
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BlockState firstState = blocks[0].defaultBlockState();
        BlockState secondState = blocks[1].defaultBlockState();
        BakedModel firstModel = dispatcher.getBlockModel(firstState);
        BakedModel secondModel = dispatcher.getBlockModel(secondState);

        context.pushTransform(matrixTransform(matrixFor(mode, false)));
        context.bakedModelConsumer().accept(firstModel, firstState);
        context.popTransform();

        context.pushTransform(matrixTransform(matrixFor(mode, true)));
        context.bakedModelConsumer().accept(secondModel, secondState);
        context.popTransform();
    }

    /** 让"第二块（上/南/东半）"落到另一半所需的额外平移；平放时只是上移半格。 */
    private static Matrix4f matrixFor(VerticalSlabMode mode, boolean second) {
        Matrix4f m = new Matrix4f();
        switch (mode) {
            case SLAB -> {
                if (second) {
                    m.translate(0.0F, 0.5F, 0.0F);
                }
            }
            case VERTICAL_NS -> {
                m.translate(0.0F, 0.0F, second ? 1.0F : 0.5F);
                m.rotateX((float) Math.toRadians(-90.0));
            }
            case VERTICAL_EW -> {
                m.translate(second ? 1.0F : 0.5F, 0.0F, 0.0F);
                m.rotateZ((float) Math.toRadians(90.0));
            }
        }
        return m;
    }

    private static RenderContext.QuadTransform matrixTransform(Matrix4f matrix) {
        return quad -> {
            for (int i = 0; i < 4; i++) {
                Vector3f p = new Vector3f(quad.x(i), quad.y(i), quad.z(i));
                matrix.transformPosition(p);
                quad.pos(i, p);
            }
            return true;
        };
    }
}
