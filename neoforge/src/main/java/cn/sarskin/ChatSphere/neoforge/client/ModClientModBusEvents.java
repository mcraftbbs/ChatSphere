package cn.sarskin.ChatSphere.neoforge.client;

import cn.sarskin.ChatSphere.client.hud.ChatHudOverlay;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * RegisterGuiLayersEvent fires on the MOD bus. Registered manually from
 * {@link ModClientSetup} (client-only reflective entry) to avoid the deprecated
 * EventBusSubscriber bus() attribute and to keep server classpaths clean.
 */
public class ModClientModBusEvents {

    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ChatHudOverlay.HUD_ID, ChatHudOverlay.INSTANCE);
    }
}
