package cn.sarskin.ChatSphere.forge.client;

import cn.sarskin.ChatSphere.client.hud.ChatHudOverlay;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Mod bus events; registered from ModClientSetup so servers never load this class. */
public final class ModClientModBusEvents {
    private ModClientModBusEvents() {}

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(ChatHudOverlay.HUD_ID.getPath(),
                (gui, guiGraphics, partialTick, screenWidth, screenHeight)
                        -> ChatHudOverlay.INSTANCE.render(guiGraphics, partialTick));
    }
}
