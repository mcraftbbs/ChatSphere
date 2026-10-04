package cn.sarskin.ChatSphere.forge.client;

import cn.sarskin.ChatSphere.ModInfo;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

public final class ModKeyMappings {
    public static final String KEY_CATEGORY = "key.categories." + ModInfo.MODID;

    public static final KeyMapping OPEN_CONFIG_KEY = new KeyMapping(
            "key." + ModInfo.MODID + ".open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KEY_CATEGORY
    );

    private ModKeyMappings() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY);
    }
}
