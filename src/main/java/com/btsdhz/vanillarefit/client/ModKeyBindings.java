package com.btsdhz.vanillarefit.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * 本模组的按键绑定。
 *
 * <p>NeoForge 原版用 {@code RegisterKeyMappingsEvent}；Fabric 侧改用
 * {@link KeyBindingHelper#registerKeyBinding(KeyMapping)}。
 */
public final class ModKeyBindings {
    public static final String KEY_CATEGORY = "key.categories.btsdhz_original";

    private static final KeyMapping CRAWL_KEY = new KeyMapping(
            "key.btsdhz_original.crawl",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            KEY_CATEGORY
    );

    private static final KeyMapping SIT_KEY = new KeyMapping(
            "key.btsdhz_original.sit",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY
    );

    /** 切换当前手持台阶/楼梯的放置逻辑(本模组竖放 <-> 原版)。 */
    private static final KeyMapping PLACEMENT_MODE_KEY = new KeyMapping(
            "key.btsdhz_original.placement_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY
    );

    private ModKeyBindings() {
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(CRAWL_KEY);
        KeyBindingHelper.registerKeyBinding(SIT_KEY);
        KeyBindingHelper.registerKeyBinding(PLACEMENT_MODE_KEY);
    }

    public static boolean isCrawlDown() {
        return CRAWL_KEY.isDown();
    }

    public static boolean consumeSit() {
        return SIT_KEY.consumeClick();
    }

    public static boolean consumePlacementMode() {
        return PLACEMENT_MODE_KEY.consumeClick();
    }
}
