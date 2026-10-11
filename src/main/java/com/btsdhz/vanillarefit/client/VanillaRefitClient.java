package com.btsdhz.vanillarefit.client;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.btsdhz.vanillarefit.ModEntities;
import com.btsdhz.vanillarefit.block.MixedSlabBlock;
import com.btsdhz.vanillarefit.client.renderer.FenceKnotRenderer;
import com.btsdhz.vanillarefit.client.renderer.SitEntityRenderer;
import com.btsdhz.vanillarefit.network.CrawlStatePayload;
import com.btsdhz.vanillarefit.network.ModClientNetworking;
import com.btsdhz.vanillarefit.network.PlacementModePayload;
import com.btsdhz.vanillarefit.network.SitTogglePayload;
import com.btsdhz.vanillarefit.util.CrawlLogic;
import com.btsdhz.vanillarefit.util.PlacementMode;
import com.btsdhz.vanillarefit.util.PlacementModeState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * 客户端入口：按键、包接收、模型插件、实体渲染器、每 tick 逻辑与放置提示线。
 * 对应 NeoForge 原版的 {@code ModClientEvents} + {@code ModKeyBindings} + {@code PlacementGuideRenderer}。
 */
public final class VanillaRefitClient implements ClientModInitializer {

    private static boolean crawlPoseApplied;
    private static boolean crawlStateSent;

    @Override
    public void onInitializeClient() {
        ModKeyBindings.register();
        ModClientNetworking.register();
        ModelLoadingPlugin.register(new RefitModelLoadingPlugin());

        EntityRendererRegistry.register(ModEntities.FENCE_KNOT, FenceKnotRenderer::new);
        EntityRendererRegistry.register(ModEntities.SIT, SitEntityRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(VanillaRefitClient::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(PlacementGuideRenderer::render);
    }

    private static void onClientTick(Minecraft mc) {
        if (mc.player == null) {
            MixedSlabBlock.cachedPlayer = null;
            crawlPoseApplied = false;
            crawlStateSent = false;
            return;
        }
        MixedSlabBlock.cachedPlayer = mc.player;

        if (mc.screen == null && ModKeyBindings.consumeSit()) {
            ClientPlayNetworking.send(new SitTogglePayload());
        }

        if (mc.screen == null && ModKeyBindings.consumePlacementMode()) {
            togglePlacementMode(mc);
        }

        boolean wantCrawl = ModKeyBindings.isCrawlDown()
                && mc.player.isAlive()
                && !mc.player.isSpectator()
                && !mc.player.getAbilities().flying
                && !mc.player.isFallFlying()
                && !mc.player.isPassenger()
                && !mc.player.isSleeping();

        if (wantCrawl) {
            // 客户端本地进入俯卧姿态：CrawlLogic 的标记会驱动 PlayerCrawlPoseMixin 强制 SWIMMING
            // （NeoForge 原版客户端用 Player#setForcedPose，那其实是 NeoForge 的扩展方法，Fabric 没有）。
            CrawlLogic.setCrawling(mc.player, true);
            crawlPoseApplied = true;
            mc.player.setSprinting(false);
        } else if (crawlPoseApplied) {
            CrawlLogic.setCrawling(mc.player, false);
            crawlPoseApplied = false;
        }

        if (wantCrawl != crawlStateSent) {
            crawlStateSent = wantCrawl;
            ClientPlayNetworking.send(new CrawlStatePayload(wantCrawl));
        }
    }

    private static void togglePlacementMode(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        boolean isSlab = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SlabBlock;
        boolean isStair = held.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof StairBlock;
        if (!isSlab && !isStair) {
            return;
        }

        PlacementMode slabMode = PlacementModeState.slab(mc.player);
        PlacementMode stairMode = PlacementModeState.stair(mc.player);
        PlacementMode newMode;
        if (isSlab) {
            slabMode = slabMode.next(false);
            newMode = slabMode;
        } else {
            stairMode = stairMode.next(true);
            newMode = stairMode;
        }

        PlacementModeState.setClient(mc.player, slabMode, stairMode);
        ClientPlayNetworking.send(PlacementModePayload.of(slabMode, stairMode));

        Component modeName = Component.translatable("placement_mode.btsdhz_original." + newMode.getSerializedName());
        mc.gui.setOverlayMessage(
                Component.translatable(
                        isSlab ? "message.btsdhz_original.placement_mode.slab"
                                : "message.btsdhz_original.placement_mode.stair",
                        modeName),
                false);
    }
}
