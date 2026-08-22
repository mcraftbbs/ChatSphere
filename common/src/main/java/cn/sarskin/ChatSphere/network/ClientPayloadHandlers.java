package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.client.ModVoiceMessagesIntegration;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/** Client-side payload handling (runs on the client game thread). */
public final class ClientPayloadHandlers {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-ClientNet");
    private ClientPayloadHandlers() {}

    /** Defensive wrapper: after disconnect the player may be null; log and drop failures instead of crashing the client task loop. */
    public static void safe(String what, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            LOGGER.warn("Client task '{}' failed (ignored)", what, t);
        }
    }

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
        ClientboundMessageSyncPayload.StoredMessage sm = p.message();
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
                    sm.isInput());
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
                    sm.itemNbt(),
                    sm.messageId());
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
                    sm.itemNbt(),
                    sm.messageId());
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

    public static void configSync(ClientboundConfigSyncPayload p) {
        for (Map.Entry<String, String> e : p.values().entrySet()) {
            ModServerConfig.applyValue(e.getKey(), e.getValue());
        }
    }

    public static void voice(ClientboundVoicePacket p) {
        ModVoiceMessagesIntegration.handleIncomingVoice(
                p.voiceMessageId(), p.senderUuid(), p.conversationId(), p.conversationType(), p.frameCount(), p.audioData());
    }

    public static void channelRenamed(ClientboundChannelRenamedPayload p) {
        ChatHistoryManager.getInstance().applyChannelRename(p.oldId(), p.newId());
    }

    /** Received on the client thread. Re-validates before touching disk (defense in depth). */
    public static void customEmoji(ClientboundCustomEmojiPayload p) {
        if (p.action() == ClientboundCustomEmojiPayload.Action.ADD) {
            cn.sarskin.ChatSphere.client.emoji.CustomEmojiRegistry.receiveServerAdd(p.name(), p.data(), p.channelId());
        } else if (p.action() == ClientboundCustomEmojiPayload.Action.DELETE) {
            cn.sarskin.ChatSphere.client.emoji.CustomEmojiRegistry.receiveServerDelete(p.name(), p.channelId());
        }
    }
}
