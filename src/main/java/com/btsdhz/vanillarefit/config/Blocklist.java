package com.btsdhz.vanillarefit.config;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * 方块名单：决定「哪些方块允许本模组给它加竖形态」。
 *
 * <p>格式（配置项 blocklist，每行一条，从上往下匹配，<b>第一条命中的生效</b>；
 * 一条都没命中时默认<b>允许</b>）：
 *
 * <pre>
 *   +*                        全部允许（通常放第一条当兜底）
 *   -*                        全部禁止
 *   +minecraft:oak_slab       按方块 ID
 *   +somemod:*                按模组（命名空间）
 *   +#minecraft:wooden_slabs  按方块标签
 * </pre>
 */
public final class Blocklist {

    /** 条目类型。 */
    public enum Kind {
        GLOBAL,
        BLOCK_ID,
        MOD_ID,
        BLOCK_TAG
    }

    public record Entry(boolean allow, Kind kind, String value, TagKey<Block> tag) {

        public boolean matches(Block block, ResourceLocation id) {
            return switch (kind) {
                case GLOBAL -> true;
                case BLOCK_ID -> value.equals(id.toString());
                case MOD_ID -> value.equals(id.getNamespace());
                case BLOCK_TAG -> tag != null && block.defaultBlockState().is(tag);
            };
        }

        /** 自查命令里的显示形式。 */
        public String describe() {
            String sign = allow ? "+" : "-";
            return switch (kind) {
                case GLOBAL -> sign + "*  全部方块";
                case BLOCK_ID -> sign + value + "  方块";
                case MOD_ID -> sign + value + ":*  模组";
                case BLOCK_TAG -> sign + "#" + value + "  标签";
            };
        }
    }

    private static final List<Entry> DEFAULT_ENTRIES = List.of(new Entry(true, Kind.GLOBAL, "*", null));

    private Blocklist() {
    }

    /** 每次调用都重新读配置，这样改完配置文件重载就立刻生效。 */
    public static List<Entry> entries() {
        List<String> raw;
        try {
            raw = BtsdhzConfig.BLOCKLIST.get();
        } catch (RuntimeException notLoadedYet) {
            return DEFAULT_ENTRIES;
        }
        List<Entry> parsed = new ArrayList<>(raw.size());
        for (String line : raw) {
            Entry entry = parse(line);
            if (entry != null) {
                parsed.add(entry);
            }
        }
        return parsed.isEmpty() ? DEFAULT_ENTRIES : parsed;
    }

    /** 该方块是否允许本模组给它加竖形态。 */
    public static boolean allows(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        for (Entry entry : entries()) {
            if (entry.matches(block, id)) {
                return entry.allow();
            }
        }
        return true;
    }

    /** 解析一行；写错了就打印一条警告并忽略这一行。 */
    private static Entry parse(String raw) {
        String line = raw == null ? "" : raw.trim();
        if (line.length() < 2) {
            BtsdhzOriginal.LOGGER.warn("[原版精修] 方块名单条目 \"{}\" 太短，已忽略（应形如 +* / -minecraft:oak_slab / +mod:* / +#minecraft:wooden_slabs）", raw);
            return null;
        }
        boolean allow;
        if (line.charAt(0) == '+') {
            allow = true;
        } else if (line.charAt(0) == '-') {
            allow = false;
        } else {
            BtsdhzOriginal.LOGGER.warn("[原版精修] 方块名单条目 \"{}\" 必须以 + 或 - 开头，已忽略", raw);
            return null;
        }

        String body = line.substring(1);
        if (body.equals("*")) {
            return new Entry(allow, Kind.GLOBAL, "*", null);
        }
        if (body.startsWith("#")) {
            String tagName = body.substring(1);
            try {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagName));
                return new Entry(allow, Kind.BLOCK_TAG, tagName, tag);
            } catch (RuntimeException invalid) {
                BtsdhzOriginal.LOGGER.warn("[原版精修] 方块名单里的标签 \"{}\" 不合法，已忽略", tagName);
                return null;
            }
        }
        if (body.endsWith(":*")) {
            String mod = body.substring(0, body.length() - 2);
            if (mod.isEmpty() || mod.contains(":")) {
                BtsdhzOriginal.LOGGER.warn("[原版精修] 方块名单里的模组 \"{}\" 不合法，已忽略", body);
                return null;
            }
            return new Entry(allow, Kind.MOD_ID, mod, null);
        }
        try {
            ResourceLocation.parse(body);
            return new Entry(allow, Kind.BLOCK_ID, body, null);
        } catch (RuntimeException invalid) {
            BtsdhzOriginal.LOGGER.warn("[原版精修] 方块名单里的方块 ID \"{}\" 不合法，已忽略", body);
            return null;
        }
    }
}
