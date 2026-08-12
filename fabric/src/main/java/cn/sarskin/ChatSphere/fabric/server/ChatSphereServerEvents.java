package cn.sarskin.ChatSphere.fabric.server;

import cn.sarskin.ChatSphere.server.ServerHooks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ChatSphereServerEvents {
    private ChatSphereServerEvents() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer sp = handler.getPlayer();
            if (sp != null) ServerHooks.onPlayerJoin(sp);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerHooks::onServerStopping);
    }
}
