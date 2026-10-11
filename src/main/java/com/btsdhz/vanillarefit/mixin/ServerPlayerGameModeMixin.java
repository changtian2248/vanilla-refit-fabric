package com.btsdhz.vanillarefit.mixin;

import com.btsdhz.vanillarefit.util.MixedSlabBreakHandler;
import com.btsdhz.vanillarefit.block.MixedSlabBlock;
import com.btsdhz.vanillarefit.event.CreateSymmetryCompat;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端破坏方块。
 *
 * <p>潜行（按住 Shift）时：若目标是堆叠半砖（混合半砖或原版 DOUBLE），只拆被准星命中的
 * 那一半，保留另一半。非潜行时走原版“整个拆除”，会掉落整格的所有半砖物品。
 *
 * <p>因为这两条路都是本模组完全接管原版 {@code destroyBlock} 的，原版发出的破坏事件就不会触发，
 * 这里在接管前自己补发一次。NeoForge 原版用的是 {@code CommonHooks.fireBlockBreak(...)}；
 * Fabric 侧改用等价的 {@link PlayerBlockBreakEvents#BEFORE}（返回 false 即取消），
 * 让其它模组/插件靠破坏事件做的联动（例如机械动力对称之杖）不“断联”。
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow()
    @Final
    protected ServerPlayer player;

    @Shadow()
    protected ServerLevel level;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void btsdhz_original$breakMixedHalf(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        BlockState state = this.level.getBlockState(pos);
        boolean sneaking = this.player.isShiftKeyDown();
        boolean mixedSlab = state.getBlock() instanceof MixedSlabBlock;
        boolean vanillaDouble = state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE;
        // 只有这两种情况本模组才接管；其余方块完全走原版（连带原版自己的 Break 事件）
        boolean takesOver = sneaking ? (mixedSlab || vanillaDouble) : mixedSlab;
        if (!takesOver) {
            return;
        }
        // 破坏前先把镜像那边的情况记下来（此刻源格与镜像格的方块都还在）
        CreateSymmetryCompat.beginBreak(this.level, this.player, pos, state, sneaking);
        // 接管前补发破坏事件（Fabric 版本：返回 false 表示取消）
        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        if (!PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(this.level, this.player, pos, state, blockEntity)) {
            CreateSymmetryCompat.endBreak();
            cir.setReturnValue(false);
            cir.cancel();
            return;
        }
        boolean handled = sneaking
                ? MixedSlabBreakHandler.tryBreakHalf(this.level, pos, state, this.player)
                : MixedSlabBreakHandler.dropWholeMixed(this.level, pos, state, this.player);
        CreateSymmetryCompat.endBreak();
        if (handled) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}
