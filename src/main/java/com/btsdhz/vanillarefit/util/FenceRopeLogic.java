package com.btsdhz.vanillarefit.util;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.btsdhz.vanillarefit.entity.FenceKnotEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 栅栏拴绳功能的共享逻辑:待连接状态、查找/创建绳结、建立连接。
 *
 * <p>NeoForge 原版把"待连接位置"存进 {@code getPersistentData()}；Fabric 侧改用
 * 一个非持久化的 Data Attachment（该状态本身就是临时的，换手/登出即丢，无需落盘）。
 */
public final class FenceRopeLogic {
    /** 待连接位置（临时状态，不落盘）。 */
    private static final AttachmentType<BlockPos> PENDING = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(BtsdhzOriginal.MOD_ID, "fence_knot_pending"));

    private FenceRopeLogic() {
    }

    public static BlockPos getPending(Player player) {
        return ((AttachmentTarget) player).getAttached(PENDING);
    }

    public static void setPending(Player player, BlockPos pos) {
        ((AttachmentTarget) player).setAttached(PENDING, pos);
    }

    public static void clearPending(Player player) {
        ((AttachmentTarget) player).removeAttached(PENDING);
    }

    public static boolean hasPlayerLeashedMobs(Player player, Level level, BlockPos pos) {
        double r = 7.0D;
        AABB aabb = new AABB(pos.getX() - r, pos.getY() - r, pos.getZ() - r,
                pos.getX() + r, pos.getY() + r, pos.getZ() + r);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, aabb)) {
            if (entity instanceof Leashable leashable && leashable.getLeashHolder() == player) {
                return true;
            }
        }
        return false;
    }

    public static FenceKnotEntity getOrCreateKnot(Level level, BlockPos pos) {
        FenceKnotEntity existing = findKnot(level, pos);
        if (existing != null) {
            return existing;
        }
        FenceKnotEntity knot = new FenceKnotEntity(level, pos);
        level.addFreshEntity(knot);
        return knot;
    }

    public static FenceKnotEntity findKnot(Level level, BlockPos pos) {
        AABB search = new AABB(pos).inflate(1.0);
        for (FenceKnotEntity knot : level.getEntitiesOfClass(FenceKnotEntity.class, search)) {
            if (knot.getPos().equals(pos)) {
                return knot;
            }
        }
        return null;
    }

    /** 在 from 与 to 两根栅栏之间建立(或追加)一条拴绳连接。 */
    public static void connect(Level level, BlockPos from, BlockPos to, boolean consumed) {
        FenceKnotEntity a = getOrCreateKnot(level, from);
        FenceKnotEntity b = getOrCreateKnot(level, to);
        a.setPartner(to, consumed);
        b.setPartner(from, consumed);
        a.setPendingPlayer(null);
        b.setPendingPlayer(null);
    }
}
