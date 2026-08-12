package cn.sarskin.ChatSphere.fabric.network;

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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Global payload type registration (loader-agnostic classes only, no client-only
 * references), shared by the server and client entrypoints. Idempotent because
 * both entrypoints run in a client process.
 */
public final class FabricTypes {
    private static boolean registered;

    private FabricTypes() {}

    public static void register() {
        if (registered) return;
        registered = true;

        PayloadTypeRegistry.playC2S().register(ServerboundChannelActionPayload.TYPE, ServerboundChannelActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundPermissionCheckPayload.TYPE, ServerboundPermissionCheckPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundConfigUpdatePayload.TYPE, ServerboundConfigUpdatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundVoicePacket.TYPE, ServerboundVoicePacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ServerboundCommandMessagePayload.TYPE, ServerboundCommandMessagePayload.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(ClientboundChannelSyncPayload.TYPE, ClientboundChannelSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundMessageSyncPayload.TYPE, ClientboundMessageSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundChatPayload.TYPE, ClientboundChatPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundPermissionResponsePayload.TYPE, ClientboundPermissionResponsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundPublicChannelListPayload.TYPE, ClientboundPublicChannelListPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundBridgeInfoPayload.TYPE, ClientboundBridgeInfoPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundVoicePacket.TYPE, ClientboundVoicePacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ClientboundChannelRenamedPayload.TYPE, ClientboundChannelRenamedPayload.STREAM_CODEC);
    }
}
