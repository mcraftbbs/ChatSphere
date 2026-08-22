package cn.sarskin.ChatSphere.fabric.client.network;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mirror of NeoForge ClientChatReceivedEvent handling; ALLOW_CHAT provides ChatType.Bound plus sender GameProfile, so no ChatListener mixin is needed. */
public final class ClientChatInterception {
    private static final List<Component> sysMsgBuffer = new ArrayList<>();
    private static UUID sysMsgSender;
    private static long sysMsgFlushTime;
    private static final long SYS_MSG_DELAY_MS = 150;

    private ClientChatInterception() {}

    public static void init() {
        ClientReceiveMessageEvents.ALLOW_GAME.register(ClientChatInterception::onSystemMessage);
        ClientReceiveMessageEvents.ALLOW_CHAT.register(ClientChatInterception::onChatMessage);
    }

    private static boolean onSystemMessage(Component message, boolean overlay) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return true;
        if (overlay) return true;
        if (sysMsgBuffer.isEmpty()) {
            sysMsgSender = Util.NIL_UUID;
        }
        sysMsgBuffer.add(message);
        sysMsgFlushTime = System.currentTimeMillis() + SYS_MSG_DELAY_MS;
        return false;
    }

    private static boolean onChatMessage(Component message, PlayerChatMessage playerChatMessage,
                                         GameProfile sender, ChatType.Bound boundChatType, Instant time) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return true;

        if (!sysMsgBuffer.isEmpty()) {
            flushSysMsgBuffer();
        }

        if (sender == null || sender.getId() == null) {
            return false;
        }
        UUID senderUuid = sender.getId();
        if (senderUuid.equals(client.player.getUUID())) {
            return false;
        }

        String content = message.getString();
        // Skip VoiceMessage# broadcasts; handled by the dedicated voice relay (ChatComponentMixin → handleVoiceChatMessage).
        if (content.startsWith("VoiceMessage#")) return true;
        Component displayName = Component.literal(sender.getName() != null ? sender.getName() : Component.translatable("chatsphere.system_name").getString());

        ChatHistoryManager history = ChatHistoryManager.getInstance();

        // 1.20.1 ChatType.Bound lacks the type key, so vanilla /msg falls through to the default channel.
        history.addMessage(displayName, senderUuid, Component.literal(content),
                ChatHistoryManager.DEFAULT_CHANNEL_ID, ChatMessageData.ConversationType.CHANNEL, false);
        return false;
    }

    public static void flushSysMsgBuffer() {
        if (sysMsgBuffer.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            sysMsgBuffer.clear();
            return;
        }
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ClientPacketListener conn = mc.getConnection();
        boolean connected = conn != null && history.isServerConnected();

        Component combined;
        if (sysMsgBuffer.size() == 1) {
            combined = sysMsgBuffer.get(0);
        } else {
            MutableComponent merged = Component.literal("");
            for (int i = 0; i < sysMsgBuffer.size(); i++) {
                if (i > 0) merged = merged.append(Component.literal("\n"));
                merged = merged.append(sysMsgBuffer.get(i));
            }
            combined = merged;
        }

        // VoiceMessage# output goes via the dedicated voice relay.
        if (combined.getString().startsWith("VoiceMessage#")) {
            sysMsgBuffer.clear();
            sysMsgSender = null;
            return;
        }

        history.addCommandMessage(combined, sysMsgSender, Component.literal(""), false);

        if (connected) {
            // Console history is per-player: attribute to the local player so the server only stores/distributes it to them.
            UUID sendUuid = mc.player.getUUID();
            String json;
            try {
                json = Component.Serializer.toJson(combined);
            } catch (Exception e) {
                json = combined.getString();
            }
            conn.send(new ServerboundCustomPayloadPacket(ServerboundCommandMessagePayload.ID,
                    new ServerboundCommandMessagePayload(json, sendUuid, false).toBuf()));
        }

        sysMsgBuffer.clear();
        sysMsgSender = null;
    }
}
