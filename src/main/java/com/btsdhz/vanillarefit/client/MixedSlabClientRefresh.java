package com.btsdhz.vanillarefit.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 客户端：组合半砖的材质变了以后，要求重画它所在的区块。
 *
 * <p>组合半砖的材质是渲染时从方块实体取出来合并的（Fabric 的渲染数据附件，见
 * {@code MixedSlabBlockEntity#getRenderAttachmentData}）。只改方块实体数据、没改方块状态时，
 * 客户端不会重画这一区块，材质会一直停在旧值，直到旁边有方块更新把区块标脏才刷新。
 */
public final class MixedSlabClientRefresh {

    private MixedSlabClientRefresh() {
    }

    public static void section(Level level, BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level || minecraft.levelRenderer == null) {
            return;
        }
        minecraft.levelRenderer.setSectionDirty(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }
}
