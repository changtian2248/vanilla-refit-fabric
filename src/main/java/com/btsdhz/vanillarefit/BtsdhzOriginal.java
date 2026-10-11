package com.btsdhz.vanillarefit;

import com.btsdhz.vanillarefit.config.BtsdhzConfig;
import com.btsdhz.vanillarefit.event.CreateSymmetryCompat;
import com.btsdhz.vanillarefit.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 原版精修 — Fabric 1.21.1 主入口。
 *
 * <p>原始版本为 NeoForge 1.21.1，本类对应其 {@code BtsdhzOriginal} 构造器里的各项注册：
 * 配置、方块/物品/实体/方块实体注册、创造模式标签项、网络包（见 {@code network.ModNetworking}）
 * 与客户端按键（见 {@code client.VanillaRefitClient}）。
 */
public class BtsdhzOriginal implements ModInitializer {
    public static final String MOD_ID = "btsdhz_original";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("原版精修模组已加载！");
        // 读取/生成配置文件 config/btsdhz_original.json
        BtsdhzConfig.load();
        // 触发注册（静态字段即完成注册）
        ModBlocks.register();
        ModEntities.register();
        ModBlockEntities.register();
        // 网络包注册 + 进服下发放置逻辑 + 登出清理爬行状态
        ModNetworking.register();
        // 自查命令 /btsdhz_original blocklist
        com.btsdhz.vanillarefit.command.BtsdhzCommands.register();
        // 创造模式物品栏：把平滑石楼梯放进「建筑方块」
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(entries -> entries.accept(ModBlocks.SMOOTH_STONE_STAIRS_ITEM));
        // 每 tick 结束处理机械动力对称之杖的材质搬运（对应 NeoForge 的 LevelTickEvent.Post）
        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getAllLevels().forEach(CreateSymmetryCompat::tickLevel));
    }
}
