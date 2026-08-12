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

/**
 * Fabric chat interception, mirroring the NeoForge ClientChatReceivedEvent handling.
 * ALLOW_CHAT provides ChatType.Bound (private-message detection) plus the sender
 * GameProfile, so no ChatListener mixin is needed.
 */
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
        Component displayName = Component.literal(sender.getName() != null ? sender.getName() : Component.translatable("chatsphere.system_name").getString());

        ChatHistoryManager history = ChatHistoryManager.getInstance();

        // 1.20.1's ChatType.Bound does not expose the chat type key, so vanilla /msg
        // cannot be detected here; it falls through to the default channel. Private
        // messaging via the mod UI uses the channel protocol and is fully supported.
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

        history.addCommandMessage(combined, sysMsgSender, Component.literal(""), false);

        if (connected) {
            UUID sendUuid = sysMsgSender != null ? sysMsgSender : Util.NIL_UUID;
            String json;
            try {
                json = Component.Serializer.toJson(combined);
            } catch (Exception e) {
                json = combined.getString();
            }
            conn.send(new ServerboundCustomPayloadPacket(ServerboundCommandMessagePayload.ID,
                    new ServerboundCommandMessagePayload(json, sendUuid).toBuf()));
        }

        sysMsgBuffer.clear();
        sysMsgSender = null;
    }
}
