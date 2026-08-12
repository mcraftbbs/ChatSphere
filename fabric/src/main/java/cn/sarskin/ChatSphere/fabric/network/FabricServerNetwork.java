package cn.sarskin.ChatSphere.fabric.network;

import cn.sarskin.ChatSphere.network.ServerPayloadHandlers;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import cn.sarskin.ChatSphere.network.ServerboundPermissionCheckPayload;
import cn.sarskin.ChatSphere.network.ServerboundVoicePacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Server-side receiver registration. Kept separate from the client registration
 *  so that dedicated servers never load client-only classes. */
public final class FabricServerNetwork {
    private FabricServerNetwork() {}

    public static void init() {
        FabricTypes.register();
        // Receivers run on the netty thread; handlers do disk I/O, so run on the game thread.
        ServerPlayNetworking.registerGlobalReceiver(ServerboundChannelActionPayload.TYPE, (p, ctx) ->
                ctx.server().execute(() -> ServerPayloadHandlers.channelAction(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundPermissionCheckPayload.TYPE, (p, ctx) ->
                ctx.server().execute(() -> ServerPayloadHandlers.permissionCheck(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundConfigUpdatePayload.TYPE, (p, ctx) ->
                ctx.server().execute(() -> ServerPayloadHandlers.configUpdate(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundVoicePacket.TYPE, (p, ctx) ->
                ctx.server().execute(() -> ServerPayloadHandlers.voicePacket(ctx.player(), p)));
        ServerPlayNetworking.registerGlobalReceiver(ServerboundCommandMessagePayload.TYPE, (p, ctx) ->
                ctx.server().execute(() -> ServerPayloadHandlers.commandMessage(ctx.player(), p)));
    }
}
