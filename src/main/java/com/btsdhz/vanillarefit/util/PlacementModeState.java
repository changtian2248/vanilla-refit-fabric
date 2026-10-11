package com.btsdhz.vanillarefit.util;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 每个玩家、每种方块（台阶/楼梯）当前的放置逻辑。
 *
 * <p><b>服务端是权威且持久化的</b>：值写进玩家的持久数据（存档保存），重新打开存档后仍然记得。
 * 客户端只持有一份缓存，用于客户端预测与提示线渲染：登录时由服务端下发一次，之后每次按键
 * 切换时客户端本地更新并同步给服务端。
 *
 * <p>NeoForge 原版用 {@code Player#getPersistentData()}；Fabric 无此概念，这里改用
 * <b>Fabric Data Attachment API</b> 的持久化附件（等价于按玩家存档的 NBT），行为一致。
 */
public final class PlacementModeState {
    /** 存进玩家持久数据的附件类型。 */
    private static final AttachmentType<Integer> SLAB_MODE = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "slab_placement_mode"), Codec.INT);
    private static final AttachmentType<Integer> STAIR_MODE = AttachmentRegistry.createPersistent(
            ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "stair_placement_mode"), Codec.INT);

    /** 客户端缓存（服务端不用它）。 */
    private static final Map<UUID, PlacementMode> CLIENT_SLAB = new ConcurrentHashMap<>();
    private static final Map<UUID, PlacementMode> CLIENT_STAIR = new ConcurrentHashMap<>();

    private PlacementModeState() {
    }

    public static PlacementMode slab(Player player) {
        if (player == null) {
            return PlacementMode.MOD;
        }
        if (player.level().isClientSide()) {
            return CLIENT_SLAB.getOrDefault(player.getUUID(), PlacementMode.MOD);
        }
        return read(player, SLAB_MODE);
    }

    public static PlacementMode stair(Player player) {
        if (player == null) {
            return PlacementMode.MOD;
        }
        if (player.level().isClientSide()) {
            return CLIENT_STAIR.getOrDefault(player.getUUID(), PlacementMode.MOD);
        }
        return read(player, STAIR_MODE);
    }

    /** 客户端本地更新：按键切换、或收到服务端下发的权威值时调用。 */
    public static void setClient(Player player, PlacementMode slabMode, PlacementMode stairMode) {
        if (player == null) {
            return;
        }
        put(CLIENT_SLAB, player.getUUID(), slabMode);
        put(CLIENT_STAIR, player.getUUID(), stairMode);
    }

    /** 服务端写入玩家持久数据（随存档保存）。 */
    public static void setServer(Player player, PlacementMode slabMode, PlacementMode stairMode) {
        if (player == null) {
            return;
        }
        write(player, SLAB_MODE, slabMode);
        write(player, STAIR_MODE, stairMode);
    }

    private static PlacementMode read(Player player, AttachmentType<Integer> type) {
        Integer value = ((AttachmentTarget) player).getAttached(type);
        return value == null ? PlacementMode.MOD : PlacementMode.byOrdinal(value);
    }

    private static void write(Player player, AttachmentType<Integer> type, PlacementMode mode) {
        if (mode == null) {
            return;
        }
        AttachmentTarget target = (AttachmentTarget) player;
        // 只存非默认值，默认值就把附件去掉（存档更干净，缺失即默认）。
        if (mode == PlacementMode.MOD) {
            target.removeAttached(type);
        } else {
            target.setAttached(type, mode.ordinal());
        }
    }

    private static void put(Map<UUID, PlacementMode> map, UUID uuid, PlacementMode mode) {
        if (mode == null || mode == PlacementMode.MOD) {
            map.remove(uuid);
        } else {
            map.put(uuid, mode);
        }
    }
}
