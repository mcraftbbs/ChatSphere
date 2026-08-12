package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.client.ModVoiceMessagesIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Client-side payload handling (runs on the client game thread). */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {}

    public static void channelSync(ClientboundChannelSyncPayload p) {
        ChatHistoryManager.getInstance().applyServerChannels(p.channels(), p.knownPlayers());
    }

    public static void messageSync(Player player, ClientboundMessageSyncPayload p) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        UUID localPlayer = player != null ? player.getUUID() : null;
        history.applyServerMessages(p.messages(), localPlayer);
    }

    public static void chat(Player player, ClientboundChatPayload p) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ClientboundChatPayload.StoredMessage sm = p.message();
        UUID localPlayer = player != null ? player.getUUID() : null;
        boolean isOwn = localPlayer != null && sm.senderUuid().equals(localPlayer);

        String convId = sm.conversationId() != null ? sm.conversationId() : ChatHistoryManager.DEFAULT_CHANNEL_ID;
        ChatMessageData.ConversationType ctype;
        if ("COMMAND".equals(sm.conversationType())) {
            ctype = ChatMessageData.ConversationType.COMMAND;
        } else if ("PRIVATE".equals(sm.conversationType())) {
            ctype = ChatMessageData.ConversationType.PRIVATE;
        } else {
            ctype = ChatMessageData.ConversationType.CHANNEL;
        }

        if (ctype == ChatMessageData.ConversationType.COMMAND) {
            String cmdText = sm.content() != null && !sm.content().isEmpty() ? sm.content() : sm.senderName();
            history.addCommandMessage(
                    Component.literal(cmdText),
                    sm.senderUuid(),
                    Component.literal(""),
                    isOwn);
        } else if (isOwn) {
            // Own messages already added locally by ModChatScreen.sendChatMessage() with reply data
        } else if (ctype == ChatMessageData.ConversationType.PRIVATE) {
            Component displayName = ChatHistoryManager.resolveOtherPartyName(convId, Component.literal(sm.senderName()));
            history.addPrivateConversation(convId, displayName);
            history.addMessage(
                    Component.literal(sm.senderName()),
                    sm.senderUuid(),
                    Component.literal(sm.content()),
                    convId,
                    ctype,
                    isOwn,
                    sm.replyContent(),
                    sm.replySender(),
                    sm.itemNbt());
        } else {
            history.addMessage(
                    Component.literal(sm.senderName()),
                    sm.senderUuid(),
                    Component.literal(sm.content()),
                    convId,
                    ctype,
                    isOwn,
                    sm.replyContent(),
                    sm.replySender(),
                    sm.itemNbt());
        }
    }

    public static void permissionResponse(ClientboundPermissionResponsePayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof cn.sarskin.ChatSphere.client.screen.ConfigScreen cs) {
            cs.onPermissionResponse(p.scope(), p.allowed());
        }
    }

    public static void publicChannelList(ClientboundPublicChannelListPayload p) {
        ChatHistoryManager.getInstance().setPublicChannels(p.channels());
    }

    public static void bridgeInfo(ClientboundBridgeInfoPayload p) {
        ChatHistoryManager.getInstance().setBridgeInfo(p);
    }

    public static void voice(ClientboundVoicePacket p) {
        ModVoiceMessagesIntegration.handleIncomingVoice(
                p.voiceMessageId(), p.senderUuid(), p.conversationId(), p.conversationType(), p.frameCount(), p.audioData());
    }

    public static void channelRenamed(ClientboundChannelRenamedPayload p) {
        ChatHistoryManager.getInstance().applyChannelRename(p.oldId(), p.newId());
    }
}
