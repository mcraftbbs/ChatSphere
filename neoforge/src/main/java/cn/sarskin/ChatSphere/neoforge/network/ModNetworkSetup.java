package cn.sarskin.ChatSphere.neoforge.network;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.network.ClientPayloadHandlers;
import cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload;
import cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundChatPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.network.ClientboundVoicePacket;
import cn.sarskin.ChatSphere.network.ServerPayloadHandlers;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import cn.sarskin.ChatSphere.network.ServerboundPermissionCheckPayload;
import cn.sarskin.ChatSphere.network.ServerboundVoicePacket;
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
                (p, ctx) -> ctx.enqueueWork(() -> ServerPayloadHandlers.channelAction(ctx.player(), p))
        );
        registrar.playToClient(
                ClientboundChannelSyncPayload.TYPE,
                ClientboundChannelSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.channelSync(p))
        );
        registrar.playToClient(
                ClientboundMessageSyncPayload.TYPE,
                ClientboundMessageSyncPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.messageSync(ctx.player(), p))
        );
        registrar.playToClient(
                ClientboundChatPayload.TYPE,
                ClientboundChatPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.chat(ctx.player(), p))
        );
        registrar.playToServer(
                ServerboundPermissionCheckPayload.TYPE,
                ServerboundPermissionCheckPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ServerPayloadHandlers.permissionCheck(ctx.player(), p))
        );
        registrar.playToClient(
                ClientboundPermissionResponsePayload.TYPE,
                ClientboundPermissionResponsePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.permissionResponse(p))
        );
        registrar.playToClient(
                ClientboundPublicChannelListPayload.TYPE,
                ClientboundPublicChannelListPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.publicChannelList(p))
        );
        registrar.playToClient(
                ClientboundBridgeInfoPayload.TYPE,
                ClientboundBridgeInfoPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.bridgeInfo(p))
        );
        registrar.playToServer(
                ServerboundConfigUpdatePayload.TYPE,
                ServerboundConfigUpdatePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ServerPayloadHandlers.configUpdate(ctx.player(), p))
        );
        registrar.playToServer(
                ServerboundVoicePacket.TYPE,
                ServerboundVoicePacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ServerPayloadHandlers.voicePacket(ctx.player(), p))
        );
        registrar.playToServer(
                ServerboundCommandMessagePayload.TYPE,
                ServerboundCommandMessagePayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ServerPayloadHandlers.commandMessage(ctx.player(), p))
        );
        registrar.playToClient(
                ClientboundVoicePacket.TYPE,
                ClientboundVoicePacket.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.voice(p))
        );
        registrar.playToClient(
                ClientboundChannelRenamedPayload.TYPE,
                ClientboundChannelRenamedPayload.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.channelRenamed(p))
        );
    }
}
