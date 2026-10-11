package com.btsdhz.vanillarefit.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** 客户端接收器：服务端下发的放置逻辑同步包。 */
public final class ModClientNetworking {
    private ModClientNetworking() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(PlacementModeSyncPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> PlacementModeSyncPayload.handle(payload, context.player())));
    }
}
