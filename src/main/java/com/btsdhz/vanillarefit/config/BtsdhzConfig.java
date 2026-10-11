package com.btsdhz.vanillarefit.config;

import com.btsdhz.vanillarefit.BtsdhzOriginal;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 本模组配置文件（Fabric 版）。
 *
 * <p>NeoForge 原版用 {@code ModConfigSpec} 生成 {@code config/btsdhz_original-common.toml}；
 * Fabric 没有官方配置 API，这里用 Gson 生成等价的 {@code config/btsdhz_original.json}。
 * 对外暴露的静态字段与访问方式（{@code XXX.get()} / {@code XXX.set(...)}）刻意保持与
 * NeoForge 版一致，便于移植时各处调用点不作改动。
 */
public final class BtsdhzConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static JsonObject root = new JsonObject();

    /** 是否允许火把、灵魂火把、灯笼、灵魂灯笼在"下台阶"上贴合台阶表面。 */
    public static final BooleanValue TORCH_LANTERN_ON_SLAB =
            new BooleanValue("allowTorchLanternOnSlab", true);

    /** 是否允许灯笼、灵魂灯笼在"上台阶"下方悬挂并贴合底面。 */
    public static final BooleanValue LANTERN_UNDER_TOP_SLAB =
            new BooleanValue("allowLanternUnderTopSlab", true);

    /** 台阶放置逻辑 2 的"中央正方形"大小（比例 0~1）。 */
    public static final DoubleValue SLAB_CENTER_SQUARE_RATIO =
            new DoubleValue("slabCenterSquareRatio", 0.5D, 0.0D, 1.0D);

    /** 方块名单：哪些方块允许本模组给它加竖形态。 */
    public static final ListValue BLOCKLIST =
            new ListValue("blocklist", List.of("+*"));

    /** 实验开关：是否让其它模组的台阶也能竖放。 */
    public static final BooleanValue ALLOW_MODDED_SLABS =
            new BooleanValue("allowModdedSlabs", true);

    /** 实验开关：是否让其它模组的楼梯也能竖放。 */
    public static final BooleanValue ALLOW_MODDED_STAIRS =
            new BooleanValue("allowModdedStairs", true);

    private BtsdhzConfig() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(BtsdhzOriginal.MOD_ID + ".json");
    }

    /** 从磁盘读取配置；文件不存在或损坏时使用默认值并写回一份。 */
    public static void load() {
        Path path = file();
        if (Files.exists(path)) {
            try {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                JsonElement parsed = JsonParser.parseString(text);
                if (parsed.isJsonObject()) {
                    root = parsed.getAsJsonObject();
                }
            } catch (IOException | RuntimeException exception) {
                BtsdhzOriginal.LOGGER.warn("[原版精修] 读取配置失败，使用默认值：{}", exception.toString());
                root = new JsonObject();
            }
        }
        TORCH_LANTERN_ON_SLAB.read();
        LANTERN_UNDER_TOP_SLAB.read();
        SLAB_CENTER_SQUARE_RATIO.read();
        BLOCKLIST.read();
        ALLOW_MODDED_SLABS.read();
        ALLOW_MODDED_STAIRS.read();
        save();
    }

    /** 写回磁盘。 */
    public static void save() {
        TORCH_LANTERN_ON_SLAB.write();
        LANTERN_UNDER_TOP_SLAB.write();
        SLAB_CENTER_SQUARE_RATIO.write();
        BLOCKLIST.write();
        ALLOW_MODDED_SLABS.write();
        ALLOW_MODDED_STAIRS.write();
        // 文件顶部写一行说明字段。
        JsonObject out = new JsonObject();
        JsonObject comments = new JsonObject();
        comments.addProperty("_说明", "原版精修的配置文件。改完保存后重进游戏生效。");
        comments.addProperty("allowTorchLanternOnSlab", "火把/灯笼在下台阶上是否下移半格贴合（默认 true）。");
        comments.addProperty("allowLanternUnderTopSlab", "灯笼是否可在上台阶下方悬挂并贴合（默认 true）。");
        comments.addProperty("slabCenterSquareRatio", "台阶放置逻辑2 中央正方形边长比例 0~1（默认 0.5）。");
        comments.addProperty("blocklist", "方块名单：+* / -* / +minecraft:oak_slab / +somemod:* / +#minecraft:wooden_slabs。");
        comments.addProperty("allowModdedSlabs", "是否让其它模组的台阶也能竖放（默认 true）。");
        comments.addProperty("allowModdedStairs", "是否让其它模组的楼梯也能竖放（默认 true）。");
        out.add("_comments", comments);
        for (String key : root.keySet()) {
            out.add(key, root.get(key));
        }
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(out), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            BtsdhzOriginal.LOGGER.warn("[原版精修] 写配置失败：{}", exception.toString());
        }
    }

    private static boolean readBool(String key, boolean def) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : def;
    }

    private static double readDouble(String key, double def) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsDouble() : def;
    }

    /** 布尔配置项。 */
    public static final class BooleanValue {
        private final String key;
        private final boolean def;
        private boolean value;

        private BooleanValue(String key, boolean def) {
            this.key = key;
            this.def = def;
            this.value = def;
        }

        private void read() {
            this.value = readBool(key, def);
        }

        private void write() {
            root.addProperty(key, value);
        }

        public boolean get() {
            return value;
        }

        public void set(boolean newValue) {
            this.value = newValue;
        }
    }

    /** 浮点配置项（带范围钳制）。 */
    public static final class DoubleValue {
        private final String key;
        private final double def;
        private final double min;
        private final double max;
        private double value;

        private DoubleValue(String key, double def, double min, double max) {
            this.key = key;
            this.def = def;
            this.min = min;
            this.max = max;
            this.value = def;
        }

        private void read() {
            this.value = Math.max(min, Math.min(max, readDouble(key, def)));
        }

        private void write() {
            root.addProperty(key, value);
        }

        public double get() {
            return value;
        }

        public void set(double newValue) {
            this.value = Math.max(min, Math.min(max, newValue));
        }
    }

    /** 字符串列表配置项。 */
    public static final class ListValue {
        private final String key;
        private final List<String> def;
        private List<String> value;

        private ListValue(String key, List<String> def) {
            this.key = key;
            this.def = def;
            this.value = new ArrayList<>(def);
        }

        private void read() {
            JsonElement element = root.get(key);
            if (element != null && element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                List<String> list = new ArrayList<>(array.size());
                for (JsonElement item : array) {
                    if (item.isJsonPrimitive()) {
                        list.add(item.getAsString());
                    }
                }
                this.value = list;
            } else {
                this.value = new ArrayList<>(def);
            }
        }

        private void write() {
            JsonArray array = new JsonArray();
            for (String item : value) {
                array.add(item);
            }
            root.add(key, array);
        }

        public List<String> get() {
            return value;
        }

        public void set(List<String> newValue) {
            this.value = newValue;
        }
    }
}
