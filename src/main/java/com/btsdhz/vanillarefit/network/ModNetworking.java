package com.btsdhz.vanillarefit.network;

import com.btsdhz.vanillarefit.util.PlacementModeState;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 网络注册（对应 NeoForge 版 {@code BtsdhzOriginal#registerPayloads}）。
 *
 * <ul>
 *     <li>payload 类型注册（两侧都要）在通用入口调用；</li>
 *     <li>服务端接收器与"进服下发放置逻辑"也在此注册，客户端环境下注册无副作用；</li>
 *     <li>客户端接收器见 {@code client.ModClientNetworking}。</li>
 * </ul>
 */
public final class ModNetworking {
    private ModNetworking() {
    }

    public static void register() {
        // —— payload 类型（C2S） ——
        PayloadTypeRegistry.playC2S().register(SitTogglePayload.TYPE, SitTogglePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CrawlStatePayload.TYPE, CrawlStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PlacementModePayload.TYPE, PlacementModePayload.STREAM_CODEC);
        // —— payload 类型（S2C） ——
        PayloadTypeRegistry.playS2C().register(PlacementModeSyncPayload.TYPE, PlacementModeSyncPayload.STREAM_CODEC);

        // —— 服务端接收器：在服务端主线程处理包 ——
        ServerPlayNetworking.registerGlobalReceiver(SitTogglePayload.TYPE,
                (payload, context) -> context.player().server.execute(
                        () -> SitTogglePayload.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(CrawlStatePayload.TYPE,
                (payload, context) -> context.player().server.execute(
                        () -> CrawlStatePayload.handle(payload, context.player())));
        ServerPlayNetworking.registerGlobalReceiver(PlacementModePayload.TYPE,
                (payload, context) -> context.player().server.execute(
                        () -> PlacementModePayload.handle(payload, context.player())));

        // —— 玩家进服：把存档里记住的放置逻辑下发给客户端 ——
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ServerPlayNetworking.send(player, new PlacementModeSyncPayload(
                    PlacementModeState.slab(player).ordinal(),
                    PlacementModeState.stair(player).ordinal()));
        });

        // —— 玩家登出：清掉手动爬行标记 ——
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.btsdhz.vanillarefit.util.CrawlLogic.clear(handler.getPlayer()));
    }
}
