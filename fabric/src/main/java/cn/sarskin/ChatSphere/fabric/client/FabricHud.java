package cn.sarskin.ChatSphere.fabric.client;

import cn.sarskin.ChatSphere.client.hud.ChatHudOverlay;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class FabricHud {
    private FabricHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((graphics, deltaTracker) ->
                ChatHudOverlay.INSTANCE.render(graphics, deltaTracker));
    }
}
