package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ClientboundConfigSyncPayload;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Shared server lifecycle hooks used by both the NeoForge and Fabric platforms. */
public final class ServerHooks {
    private ServerHooks() {}

    public static void onPlayerJoin(ServerPlayer sp) {
        ModServerChannels msc = ModServerChannels.getInstance(sp.server);
        msc.onPlayerJoined(sp);
        msc.sendToPlayer(sp);
        msc.sendMessagesToPlayer(sp);
        ModVoiceStorage vs = ModVoiceStorage.getInstance(sp.server);
        vs.deliverToPlayer(sp);
        ModServerEmoji.getInstance(sp.server).syncTo(sp);
        sp.connection.send(new ClientboundCustomPayloadPacket(ClientboundConfigSyncPayload.ID,
                new ClientboundConfigSyncPayload(ModServerConfig.snapshot()).toBuf()));
    }

    public static void onServerStopping(MinecraftServer server) {
        ModServerChannels msc = ModServerChannels.getInstance(server);
        msc.flush();
        ModServerChannels.removeServer(server);
        ModVoiceStorage.removeServer(server);
        ModServerEmoji.removeServer(server);
    }
}
