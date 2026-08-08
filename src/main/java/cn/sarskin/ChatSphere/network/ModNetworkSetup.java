package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.api.distmarker.Dist;

public class ModNetworkSetup {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar base = event.registrar("1.0");
        PayloadRegistrar registrar;
        if (FMLLoader.getDist() == Dist.CLIENT) {
            registrar = ModClientConfig.CONFIG.allowVanillaConnection.get()
                    ? base.optional() : base;
        } else {
            registrar = base;
        }
        registrar.playToServer(
                ServerboundChannelActionPayload.TYPE,
                ServerboundChannelActionPayload.STREAM_CODEC,
                ServerboundChannelActionPayload::handle
        );
        registrar.playToClient(
                ClientboundChannelSyncPayload.TYPE,
                ClientboundChannelSyncPayload.STREAM_CODEC,
                ClientboundChannelSyncPayload::handle
        );
        registrar.playToClient(
                ClientboundMessageSyncPayload.TYPE,
                ClientboundMessageSyncPayload.STREAM_CODEC,
                ClientboundMessageSyncPayload::handle
        );
        registrar.playToClient(
                ClientboundChatPayload.TYPE,
                ClientboundChatPayload.STREAM_CODEC,
                ClientboundChatPayload::handle
        );
        registrar.playToServer(
                ServerboundPermissionCheckPayload.TYPE,
                ServerboundPermissionCheckPayload.STREAM_CODEC,
                ServerboundPermissionCheckPayload::handle
        );
        registrar.playToClient(
                ClientboundPermissionResponsePayload.TYPE,
                ClientboundPermissionResponsePayload.STREAM_CODEC,
                ClientboundPermissionResponsePayload::handle
        );
        registrar.playToClient(
                ClientboundPublicChannelListPayload.TYPE,
                ClientboundPublicChannelListPayload.STREAM_CODEC,
                ClientboundPublicChannelListPayload::handle
        );
        registrar.playToClient(
                ClientboundBridgeInfoPayload.TYPE,
                ClientboundBridgeInfoPayload.STREAM_CODEC,
                ClientboundBridgeInfoPayload::handle
        );
        registrar.playToServer(
                ServerboundConfigUpdatePayload.TYPE,
                ServerboundConfigUpdatePayload.STREAM_CODEC,
                ServerboundConfigUpdatePayload::handle
        );
        registrar.playToServer(
                ServerboundVoicePacket.TYPE,
                ServerboundVoicePacket.STREAM_CODEC,
                ServerboundVoicePacket::handle
        );
        registrar.playToServer(
                ServerboundCommandMessagePayload.TYPE,
                ServerboundCommandMessagePayload.STREAM_CODEC,
                ServerboundCommandMessagePayload::handle
        );
        registrar.playToClient(
                ClientboundVoicePacket.TYPE,
                ClientboundVoicePacket.STREAM_CODEC,
                ClientboundVoicePacket::handle
        );
        registrar.playToClient(
                ClientboundChannelRenamedPayload.TYPE,
                ClientboundChannelRenamedPayload.STREAM_CODEC,
                ClientboundChannelRenamedPayload::handle
        );
    }
}
