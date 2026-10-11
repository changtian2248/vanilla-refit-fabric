package com.btsdhz.vanillarefit.network;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.btsdhz.vanillarefit.util.PlacementMode;
import com.btsdhz.vanillarefit.util.PlacementModeState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** 客户端 -> 服务端: 同步台阶/楼梯当前的放置逻辑。 */
public record PlacementModePayload(int slabMode, int stairMode) implements CustomPacketPayload {
    public static final Type<PlacementModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "placement_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlacementModePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.slabMode());
                        buffer.writeVarInt(payload.stairMode());
                    },
                    buffer -> new PlacementModePayload(buffer.readVarInt(), buffer.readVarInt())
            );

    public static PlacementModePayload of(PlacementMode slabMode, PlacementMode stairMode) {
        return new PlacementModePayload(slabMode.ordinal(), stairMode.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PlacementModePayload payload, Player player) {
        // 客户端切换后上报，服务端写进玩家持久数据（随存档保存）。
        PlacementModeState.setServer(
                player,
                PlacementMode.byOrdinal(payload.slabMode()),
                PlacementMode.byOrdinal(payload.stairMode())
        );
    }
}
