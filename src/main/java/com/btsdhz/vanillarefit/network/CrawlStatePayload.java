package com.btsdhz.vanillarefit.network;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.btsdhz.vanillarefit.util.CrawlLogic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** 客户端 -> 服务端: 同步手动爬行状态, 让服务端的姿态/眼高与客户端一致。 */
public record CrawlStatePayload(boolean crawling) implements CustomPacketPayload {
    public static final Type<CrawlStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "crawl_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CrawlStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBoolean(payload.crawling()),
                    buffer -> new CrawlStatePayload(buffer.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrawlStatePayload payload, Player player) {
        CrawlLogic.setCrawling(player, payload.crawling());
    }
}
