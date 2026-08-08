package cn.sarskin.ChatSphere.server;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.MinecraftServer;

public class ModServerEvents {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) return;
        ModServerChannels msc = ModServerChannels.getInstance(sp.server);
        msc.sendToPlayer(sp);
        msc.sendMessagesToPlayer(sp);
        ModVoiceStorage vs = ModVoiceStorage.getInstance(sp.server);
        vs.deliverToPlayer(sp);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        ModServerChannels msc = ModServerChannels.getInstance(server);
        msc.flush();
        ModServerChannels.removeServer(server);
        ModVoiceStorage.removeServer(server);
    }
}
