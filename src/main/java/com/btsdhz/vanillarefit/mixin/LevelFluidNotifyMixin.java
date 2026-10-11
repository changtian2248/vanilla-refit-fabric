package com.btsdhz.vanillarefit.mixin;

import com.btsdhz.vanillarefit.util.FluidType;
import com.btsdhz.vanillarefit.util.ModBlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 含流体的台阶/楼梯在邻居变化后重新安排流体 tick。
 *
 * <p>NeoForge 原版监听 {@code BlockEvent.NeighborNotifyEvent}；Fabric 无此事件，
 * 这里改为注入 {@link Level#updateNeighborsAtExceptFromFacing} 的尾部——原版的
 * {@code updateNeighborsAt} 与 {@code updateNeighborsAtExceptFromFacing} 都汇流到这一个方法，
 * 效果与「邻居通知」事件等价。
 */
@Mixin(Level.class)
public abstract class LevelFluidNotifyMixin {

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("TAIL"))
    private void btsdhz_original$onNeighborNotify(BlockPos pos, net.minecraft.world.level.block.Block sourceBlock,
                                                  Direction skipSide, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        if (level.isClientSide) {
            return;
        }
        for (Direction dir : Direction.values()) {
            if (dir == skipSide) {
                continue;
            }
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighbor = level.getBlockState(neighborPos);

            boolean isFluidHolder;
            if (neighbor.getBlock() instanceof SlabBlock) {
                isFluidHolder = true;
            } else if (neighbor.getBlock() instanceof StairBlock) {
                isFluidHolder = neighbor.hasProperty(ModBlockStateProperties.FLUID_TYPE);
            } else {
                isFluidHolder = false;
            }
            if (!isFluidHolder) {
                continue;
            }
            if (!neighbor.hasProperty(ModBlockStateProperties.FLUID_TYPE)) {
                continue;
            }

            FluidType ft = neighbor.getValue(ModBlockStateProperties.FLUID_TYPE);
            if (ft != FluidType.WATER && ft != FluidType.LAVA) {
                continue;
            }

            Fluid fluid = ft == FluidType.WATER ? Fluids.WATER : Fluids.LAVA;
            level.scheduleTick(neighborPos, fluid, fluid.getTickDelay(level));
        }
    }
}
