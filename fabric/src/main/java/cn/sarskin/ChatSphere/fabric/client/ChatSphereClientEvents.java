package cn.sarskin.ChatSphere.fabric.client;

import cn.sarskin.ChatSphere.client.ChatHintsManager;
import cn.sarskin.ChatSphere.client.ClientHooks;
import cn.sarskin.ChatSphere.client.screen.ConfigScreen;
import cn.sarskin.ChatSphere.client.screen.ModChatScreen;
import cn.sarskin.ChatSphere.fabric.client.network.ClientChatInterception;
import net.minecraft.client.Minecraft;

public final class ChatSphereClientEvents {
    private ChatSphereClientEvents() {}

    public static void onClientTick(Minecraft mc) {
        if (mc.player == null) return;
        ChatHintsManager.getInstance().tick();
        ClientChatInterception.flushSysMsgBuffer();
        while (mc.options.keyChat.consumeClick()) {
            mc.setScreen(new ModChatScreen(""));
        }
        if (mc.screen == null && mc.options.keyCommand.consumeClick()) {
            mc.setScreen(new ModChatScreen("/"));
        }
        while (FabricKeyMappings.OPEN_CONFIG_KEY.consumeClick()) {
            mc.setScreen(new ConfigScreen());
        }
    }

    public static void onClientLogin() {
        ClientHooks.onClientLogin();
    }

    public static void onClientDisconnect() {
        ClientHooks.onClientDisconnect();
    }
}
