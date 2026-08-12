package cn.sarskin.ChatSphere.fabric.network;

import cn.sarskin.ChatSphere.network.ClientPayloadHandlers;
import cn.sarskin.ChatSphere.network.ServerPayloadHandlers;
import cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundChatPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.network.ClientboundVoicePacket;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import cn.sarskin.ChatSphere.network.ServerboundPermissionCheckPayload;
import cn.sarskin.ChatSphere.network.ServerboundVoicePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Fabric 1.20.1 networking registration (legacy channel API).
 * The server registers Serverbound handlers; the client registers Clientbound handlers.
 * Keeping them in separate classes avoids loading client-only classes on dedicated servers.
 */
public final class FabricServerNetwork {
    private FabricServerNetwork() {}

    public static void init() {
        // Receivers run on the netty thread; handlers do disk I/O, so run on the game thread.
        ServerPlayNetworking.registerGlobalReceiver(ServerboundChannelActionPayload.ID,
                (server, player, networkHandler, buf, responseSender) -> {
                    ServerboundChannelActionPayload p = ServerboundChannelActionPayload.read(buf);
                    server.execute(() -> ServerPayloadHandlers.channelAction(player, p));
                });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundPermissionCheckPayload.ID,
                (server, player, networkHandler, buf, responseSender) -> {
                    ServerboundPermissionCheckPayload p = ServerboundPermissionCheckPayload.read(buf);
                    server.execute(() -> ServerPayloadHandlers.permissionCheck(player, p));
                });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundConfigUpdatePayload.ID,
                (server, player, networkHandler, buf, responseSender) -> {
                    ServerboundConfigUpdatePayload p = ServerboundConfigUpdatePayload.read(buf);
                    server.execute(() -> ServerPayloadHandlers.configUpdate(player, p));
                });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundVoicePacket.ID,
                (server, player, networkHandler, buf, responseSender) -> {
                    ServerboundVoicePacket p = ServerboundVoicePacket.read(buf);
                    server.execute(() -> ServerPayloadHandlers.voicePacket(player, p));
                });
        ServerPlayNetworking.registerGlobalReceiver(ServerboundCommandMessagePayload.ID,
                (server, player, networkHandler, buf, responseSender) -> {
                    ServerboundCommandMessagePayload p = ServerboundCommandMessagePayload.read(buf);
                    server.execute(() -> ServerPayloadHandlers.commandMessage(player, p));
                });
    }
}
