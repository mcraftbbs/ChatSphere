package cn.sarskin.ChatSphere.forge.client;

import cn.sarskin.ChatSphere.network.ClientPayloadHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Client half of the Forge envelope channel; kept out of ForgeNetwork so dedicated servers never resolve client classes. */
public final class ForgeClientDispatch {
    private ForgeClientDispatch() {}

    public static void dispatch(ResourceLocation key, FriendlyByteBuf buf) {
        if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload.ID)) {
            ClientPayloadHandlers.safe("channelSync", () -> ClientPayloadHandlers.channelSync(cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.ID)) {
            ClientPayloadHandlers.safe("messageSync", () -> ClientPayloadHandlers.messageSync(
                    net.minecraft.client.Minecraft.getInstance().player, cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChatPayload.ID)) {
            ClientPayloadHandlers.safe("chat", () -> ClientPayloadHandlers.chat(
                    net.minecraft.client.Minecraft.getInstance().player, cn.sarskin.ChatSphere.network.ClientboundChatPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload.ID)) {
            ClientPayloadHandlers.safe("permissionResponse", () -> ClientPayloadHandlers.permissionResponse(cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload.ID)) {
            ClientPayloadHandlers.safe("publicChannelList", () -> ClientPayloadHandlers.publicChannelList(cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload.ID)) {
            ClientPayloadHandlers.safe("bridgeInfo", () -> ClientPayloadHandlers.bridgeInfo(cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundVoicePacket.ID)) {
            ClientPayloadHandlers.safe("voice", () -> ClientPayloadHandlers.voice(cn.sarskin.ChatSphere.network.ClientboundVoicePacket.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload.ID)) {
            ClientPayloadHandlers.safe("channelRenamed", () -> ClientPayloadHandlers.channelRenamed(cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundConfigSyncPayload.ID)) {
            ClientPayloadHandlers.safe("configSync", () -> ClientPayloadHandlers.configSync(cn.sarskin.ChatSphere.network.ClientboundConfigSyncPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundCustomEmojiPayload.ID)) {
            ClientPayloadHandlers.safe("customEmoji", () -> ClientPayloadHandlers.customEmoji(cn.sarskin.ChatSphere.network.ClientboundCustomEmojiPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundTypingPayload.ID)) {
            ClientPayloadHandlers.safe("typing", () -> ClientPayloadHandlers.typing(cn.sarskin.ChatSphere.network.ClientboundTypingPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChatImagePayload.ID)) {
            ClientPayloadHandlers.safe("chatImage", () -> ClientPayloadHandlers.chatImage(cn.sarskin.ChatSphere.network.ClientboundChatImagePayload.read(buf)));
        }
    }
}
