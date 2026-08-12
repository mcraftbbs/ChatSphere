package cn.sarskin.ChatSphere.fabric.client;

import cn.sarskin.ChatSphere.ModInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class FabricKeyMappings {
    public static final String KEY_CATEGORY = "key.categories." + ModInfo.MODID;

    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key." + ModInfo.MODID + ".open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KEY_CATEGORY
    );

    private FabricKeyMappings() {}

    public static void init() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG_KEY);
    }
}
