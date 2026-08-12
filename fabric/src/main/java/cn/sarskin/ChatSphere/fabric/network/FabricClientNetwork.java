package cn.sarskin.ChatSphere.fabric.network;

import cn.sarskin.ChatSphere.network.ClientPayloadHandlers;
import cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundChatPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.network.ClientboundVoicePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side channel registration (client entrypoint only). */
public final class FabricClientNetwork {
    private FabricClientNetwork() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ClientboundChannelSyncPayload.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundChannelSyncPayload payload = ClientboundChannelSyncPayload.read(buf);
                    client.execute(() -> ClientPayloadHandlers.channelSync(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundMessageSyncPayload.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundMessageSyncPayload payload = ClientboundMessageSyncPayload.read(buf);
                    client.execute(() -> ClientPayloadHandlers.messageSync(client.player, payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundChatPayload.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundChatPayload payload = ClientboundChatPayload.read(buf);
                    client.execute(() -> ClientPayloadHandlers.chat(client.player, payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundPermissionResponsePayload.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundPermissionResponsePayload payload = ClientboundPermissionResponsePayload.read(buf);
                    client.execute(() -> ClientPayloadHandlers.permissionResponse(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundPublicChannelListPayload.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundPublicChannelListPayload payload = ClientboundPublicChannelListPayload.read(buf);
                    client.execute(() -> ClientPayloadHandlers.publicChannelList(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundBridgeInfoPayload.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundBridgeInfoPayload payload = ClientboundBridgeInfoPayload.read(buf);
                    client.execute(() -> ClientPayloadHandlers.bridgeInfo(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundVoicePacket.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundVoicePacket payload = ClientboundVoicePacket.read(buf);
                    client.execute(() -> ClientPayloadHandlers.voice(payload));
                });
        ClientPlayNetworking.registerGlobalReceiver(ClientboundChannelRenamedPayload.ID,
                (client, networkHandler, buf, responseSender) -> {
                    ClientboundChannelRenamedPayload payload = ClientboundChannelRenamedPayload.read(buf);
                    client.execute(() -> ClientPayloadHandlers.channelRenamed(payload));
                });
    }
}
