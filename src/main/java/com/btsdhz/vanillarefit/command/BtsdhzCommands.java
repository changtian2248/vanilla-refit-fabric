package com.btsdhz.vanillarefit.command;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.btsdhz.vanillarefit.config.Blocklist;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;

/**
 * 自查命令：{@code /btsdhz_original blocklist}
 *
 * <p>NeoForge 原版用 {@code RegisterCommandsEvent}；Fabric 侧改用
 * {@link CommandRegistrationCallback} 注册。
 */
public final class BtsdhzCommands {

    private BtsdhzCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        Commands.literal(BtsdhzOriginal.MOD_ID)
                                .then(Commands.literal("blocklist")
                                        .executes(BtsdhzCommands::reportBlocklist))));
    }

    private static int reportBlocklist(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<Blocklist.Entry> entries = Blocklist.entries();

        source.sendSuccess(() -> Component.literal("方块名单（从上往下匹配，第一条命中生效，都没命中则允许）："), false);
        for (Blocklist.Entry entry : entries) {
            source.sendSuccess(() -> Component.literal("  " + entry.describe()), false);
        }

        int slabs = 0;
        int slabsDenied = 0;
        int stairs = 0;
        int stairsDenied = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean allowed = Blocklist.allows(block);
            if (block instanceof SlabBlock) {
                slabs++;
                if (!allowed) {
                    slabsDenied++;
                }
            } else if (block instanceof StairBlock) {
                stairs++;
                if (!allowed) {
                    stairsDenied++;
                }
            }
        }
        int totalSlabs = slabs;
        int deniedSlabs = slabsDenied;
        int totalStairs = stairs;
        int deniedStairs = stairsDenied;
        source.sendSuccess(() -> Component.literal(
                "半砖 " + totalSlabs + " 个：允许 " + (totalSlabs - deniedSlabs) + "，禁用 " + deniedSlabs), false);
        source.sendSuccess(() -> Component.literal(
                "楼梯 " + totalStairs + " 个：允许 " + (totalStairs - deniedStairs) + "，禁用 " + deniedStairs), false);
        return totalSlabs + totalStairs;
    }
}
