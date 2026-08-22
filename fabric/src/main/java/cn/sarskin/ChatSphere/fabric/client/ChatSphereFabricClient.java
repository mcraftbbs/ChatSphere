package cn.sarskin.ChatSphere.fabric.client;

import cn.sarskin.ChatSphere.fabric.client.network.ClientChatInterception;
import cn.sarskin.ChatSphere.fabric.network.FabricClientNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatSphereFabricClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(ChatSphereFabricClient.class);

    @Override
    public void onInitializeClient() {
        LOGGER.info("ChatSphere (fabric) client ready");
        FabricClientNetwork.init();
        ClientChatInterception.init();
        FabricKeyMappings.init();
        FabricHud.init();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ChatSphereClientEvents.onClientLogin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ChatSphereClientEvents.onClientDisconnect());
        // START_CLIENT_TICK runs before vanilla consumes the chat key; END_CLIENT_TICK would be too late.
        ClientTickEvents.START_CLIENT_TICK.register(ChatSphereClientEvents::onClientTick);
    }
}
