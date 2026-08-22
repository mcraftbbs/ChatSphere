package cn.sarskin.ChatSphere.fabric.network;

import cn.sarskin.ChatSphere.network.ClientPayloadHandlers;
import cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundChatPayload;
import cn.sarskin.ChatSphere.network.ClientboundConfigSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundCustomEmojiPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.network.ClientboundVoicePacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-side receiver registration (client entrypoint only). */
public final class FabricClientNetwork {
    private FabricClientNetwork() {}

    public static void init() {
        FabricTypes.register();
        ClientPlayNetworking.registerGlobalReceiver(ClientboundChannelSyncPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("channelSync", () -> ClientPayloadHandlers.channelSync(p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundMessageSyncPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("messageSync", () -> ClientPayloadHandlers.messageSync(ctx.player(), p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundChatPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("chat", () -> ClientPayloadHandlers.chat(ctx.player(), p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundPermissionResponsePayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("permissionResponse", () -> ClientPayloadHandlers.permissionResponse(p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundPublicChannelListPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("publicChannelList", () -> ClientPayloadHandlers.publicChannelList(p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundBridgeInfoPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("bridgeInfo", () -> ClientPayloadHandlers.bridgeInfo(p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundVoicePacket.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("voice", () -> ClientPayloadHandlers.voice(p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundChannelRenamedPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("channelRenamed", () -> ClientPayloadHandlers.channelRenamed(p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundConfigSyncPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("configSync", () -> ClientPayloadHandlers.configSync(p))));
        ClientPlayNetworking.registerGlobalReceiver(ClientboundCustomEmojiPayload.TYPE, (p, ctx) ->
                ctx.client().execute(() -> ClientPayloadHandlers.safe("customEmoji", () -> ClientPayloadHandlers.customEmoji(p))));
    }
}
