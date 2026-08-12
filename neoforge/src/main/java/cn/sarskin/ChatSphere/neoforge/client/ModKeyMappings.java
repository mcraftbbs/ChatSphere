package cn.sarskin.ChatSphere.neoforge.client;

import cn.sarskin.ChatSphere.neoforge.ModMain;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

public class ModKeyMappings {
    public static final String KEY_CATEGORY = "key.categories." + ModMain.MODID;

    public static final Lazy<KeyMapping> OPEN_CONFIG_KEY = Lazy.of(() -> new KeyMapping(
            "key." + ModMain.MODID + ".open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KEY_CATEGORY
    ));

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG_KEY.get());
    }
}
