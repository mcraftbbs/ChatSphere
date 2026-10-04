package cn.sarskin.ChatSphere.forge.server;

import cn.sarskin.ChatSphere.server.ServerHooks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ModServerEvents {
    private ModServerEvents() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ModServerEvents.class);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
            ServerHooks.onPlayerJoin(sp);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerHooks.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerHooks.onServerStopping(event.getServer());
    }
}
