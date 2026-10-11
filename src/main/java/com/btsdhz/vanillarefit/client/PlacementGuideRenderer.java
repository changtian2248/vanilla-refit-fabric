package com.btsdhz.vanillarefit.client;

import com.btsdhz.vanillarefit.util.PlacementMode;
import com.btsdhz.vanillarefit.util.PlacementModeState;
import com.btsdhz.vanillarefit.util.SlabPlacementRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 手持台阶/楼梯时，在准星对着的方块面上额外画“放置区域”提示线。
 *
 * <p>NeoForge 原版挂在 {@code RenderHighlightEvent.Block} 上；Fabric 侧改挂
 * {@link net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents#AFTER_ENTITIES}，
 * 自己从 {@code Minecraft#hitResult} 取准星目标。绘制位置按方块整数坐标平移（世界渲染矩阵
 * 已经以相机为原点）。
 */
public final class PlacementGuideRenderer {

    private static final float R = 0.0F;
    private static final float G = 0.0F;
    private static final float B = 0.0F;
    private static final float A = 0.4F;

    private static final double FACE_EPS = 1.0E-3;

    private static final float[][] SLAB_EDGES = {
            {0F, 0F, 1F, 1F},
            {1F, 0F, 0F, 1F},
    };

    private static final float[][] STAIR_EDGES = {
            {0.5F, 0F, 0.5F, 1F},
            {0F, 0.5F, 1F, 0.5F},
    };

    private static final float[][] SIDE_EDGES = {
            {0F, 0.5F, 1F, 0.5F},
    };

    private static double cachedSquareRatio = -1.0D;
    private static float[][] cachedSquare = new float[0][];
    private static float[][] cachedTruncatedDiagonals = SLAB_EDGES;

    private PlacementGuideRenderer() {
    }

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || context.matrixStack() == null
                || context.consumers() == null) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit)) {
            return;
        }

        ItemStack held = mc.player.getMainHandItem();
        boolean slab = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock;
        boolean stair = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof StairBlock;
        if (!slab && !stair) {
            return;
        }
        PlacementMode mode = slab ? PlacementModeState.slab(mc.player) : PlacementModeState.stair(mc.player);
        if (mode == PlacementMode.VANILLA || mode == PlacementMode.MOD_3) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        Direction face = hit.getDirection();

        double reach = mc.player.blockInteractionRange() + 1.0D;
        if (mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > reach * reach) {
            return;
        }

        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape;
        try {
            shape = state.getShape(mc.level, pos);
        } catch (RuntimeException exception) {
            return;
        }
        if (shape.isEmpty()) {
            return;
        }

        Direction.Axis axis = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? shape.max(axis)
                : shape.min(axis);

        if (!isFaceComplete(shape, face, plane)) {
            return;
        }
        if (state.getBlock() instanceof SlabBlock && plane > 1.0E-6 && plane < 1.0 - 1.0E-6) {
            return;
        }

        boolean horizontalFace = face == Direction.UP || face == Direction.DOWN;

        PoseStack poseStack = context.matrixStack();
        VertexConsumer consumer = context.consumers().getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
        PoseStack.Pose pose = poseStack.last();

        if (horizontalFace && slab) {
            if (mode == PlacementMode.MOD_2) {
                updateLogic2Edges();
                drawEdges(consumer, pose, face, plane, cachedTruncatedDiagonals);
                drawEdges(consumer, pose, face, plane, cachedSquare);
            } else {
                drawEdges(consumer, pose, face, plane, SLAB_EDGES);
            }
        } else if (horizontalFace) {
            drawEdges(consumer, pose, face, plane, STAIR_EDGES);
        } else {
            drawEdges(consumer, pose, face, plane, SIDE_EDGES);
        }
        poseStack.popPose();

        if (context.consumers() instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch(RenderType.lines());
        }
    }

    private static void drawEdges(VertexConsumer consumer, PoseStack.Pose pose, Direction face, double plane,
                                  float[][] edges) {
        for (float[] edge : edges) {
            addEdge(consumer, pose, face, plane, edge[0], edge[1], edge[2], edge[3]);
        }
    }

    private static void updateLogic2Edges() {
        double ratio = SlabPlacementRules.centerSquareRatio();
        if (ratio == cachedSquareRatio) {
            return;
        }
        cachedSquareRatio = ratio;

        if (ratio <= 0.0D) {
            cachedSquare = new float[0][];
            cachedTruncatedDiagonals = SLAB_EDGES;
            return;
        }
        if (ratio >= 1.0D) {
            cachedSquare = new float[0][];
            cachedTruncatedDiagonals = new float[0][];
            return;
        }

        float min = (float) (0.5D - ratio / 2.0D);
        float max = (float) (0.5D + ratio / 2.0D);
        cachedSquare = new float[][]{
                {min, min, max, min},
                {max, min, max, max},
                {max, max, min, max},
                {min, max, min, min},
        };
        cachedTruncatedDiagonals = new float[][]{
                {0F, 0F, min, min},
                {max, max, 1F, 1F},
                {1F, 0F, max, min},
                {min, max, 0F, 1F},
        };
    }

    private static boolean isFaceComplete(VoxelShape shape, Direction face, double plane) {
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double from = positive ? plane - FACE_EPS : plane;
        double to = positive ? plane : plane + FACE_EPS;
        VoxelShape fullFace = switch (face.getAxis()) {
            case X -> Shapes.box(from, 0.0, 0.0, to, 1.0, 1.0);
            case Y -> Shapes.box(0.0, from, 0.0, 1.0, to, 1.0);
            case Z -> Shapes.box(0.0, 0.0, from, 1.0, 1.0, to);
        };
        return !Shapes.joinIsNotEmpty(fullFace, shape, BooleanOp.ONLY_FIRST);
    }

    private static void addEdge(VertexConsumer consumer, PoseStack.Pose pose, Direction face, double plane,
                                float u1, float v1, float u2, float v2) {
        float[] a = mapFace(face, plane, u1, v1);
        float[] b = mapFace(face, plane, u2, v2);
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float dz = b[2] - a[2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length > 1.0E-6F) {
            dx /= length;
            dy /= length;
            dz /= length;
        }
        addVertex(consumer, pose, a, dx, dy, dz);
        addVertex(consumer, pose, b, dx, dy, dz);
    }

    private static float[] mapFace(Direction face, double plane, float u, float v) {
        float p = (float) plane;
        return switch (face) {
            case UP, DOWN -> new float[]{u, p, v};
            case NORTH, SOUTH -> new float[]{u, v, p};
            case WEST, EAST -> new float[]{p, v, u};
        };
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float[] p, float nx, float ny, float nz) {
        consumer.addVertex(pose, p[0], p[1], p[2])
                .setColor(R, G, B, A)
                .setNormal(pose, nx, ny, nz);
    }
}
