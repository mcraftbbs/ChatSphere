package cn.sarskin.ChatSphere.neoforge.client;

import cn.sarskin.ChatSphere.client.ChatHintsManager;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.neoforge.ModMain;
import cn.sarskin.ChatSphere.client.screen.ConfigScreen;
import cn.sarskin.ChatSphere.client.screen.ModChatScreen;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ModMain.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    // Buffer for grouping consecutive system messages (e.g. /help output sent as separate packets)
    private static final List<Component> sysMsgBuffer = new ArrayList<>();
    private static UUID sysMsgSender;
    private static long sysMsgFlushTime;
    private static final long SYS_MSG_DELAY_MS = 150;

    private static void flushSysMsgBuffer() {
        if (sysMsgBuffer.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { sysMsgBuffer.clear(); return; }
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
            RegistryAccess access = mc.level != null ? mc.level.registryAccess() : RegistryAccess.EMPTY;
            String json;
            try {
                json = Component.Serializer.toJson(combined, access);
            } catch (Exception e) {
                json = combined.getString();
            }
            conn.send(new ServerboundCustomPayloadPacket(
                    new ServerboundCommandMessagePayload(json, sendUuid)));
        }

        sysMsgBuffer.clear();
        sysMsgSender = null;
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChatHintsManager.getInstance().tick();

        if (!sysMsgBuffer.isEmpty() && System.currentTimeMillis() >= sysMsgFlushTime) {
            flushSysMsgBuffer();
        }

        while (mc.options.keyChat.consumeClick()) {
            mc.setScreen(new ModChatScreen(""));
        }

        if (mc.screen == null && mc.options.keyCommand.consumeClick()) {
            mc.setScreen(new ModChatScreen("/"));
        }

        while (ModKeyMappings.OPEN_CONFIG_KEY.get().consumeClick()) {
            mc.setScreen(new ConfigScreen());
        }
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        cn.sarskin.ChatSphere.client.ClientHooks.onClientLogin();
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        flushSysMsgBuffer();
        cn.sarskin.ChatSphere.client.ClientHooks.onClientDisconnect();
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Cancel every chat event to hide vanilla chat display
        event.setCanceled(true);

        Component message = event.getMessage();
        UUID sender = event.getSender();

        // ALL system messages (screenshots, command feedback, overlays, etc.) → COMMAND console
        if (event.isSystem()) {
            // For system events from ChatListener, get the overlay flag
            if (event instanceof ClientChatReceivedEvent.System sys && sys.isOverlay()) {
                return;
            }
            // Buffer system messages to group multi-packet output (e.g. /help)
            if (sysMsgBuffer.isEmpty()) {
                sysMsgSender = sender != null ? sender : Util.NIL_UUID;
            }
            sysMsgBuffer.add(message);
            sysMsgFlushTime = System.currentTimeMillis() + SYS_MSG_DELAY_MS;
            return;
        }

        // Non-system message: flush any pending system message buffer first
        if (!sysMsgBuffer.isEmpty()) {
            flushSysMsgBuffer();
        }

        String text = message.getString();
        boolean isOwn = sender.equals(mc.player.getUUID());

        // Own messages (echo from server) - already added locally by ModChatScreen.sendMessage()
        if (isOwn) {
            return;
        }

        Component senderName;
        String content;

        if (text.startsWith("<") && text.contains("> ")) {
            int endBracket = text.indexOf("> ");
            senderName = Component.literal(text.substring(1, endBracket));
            content = text.substring(endBracket + 2);
        } else {
            int colonIndex = text.indexOf(": ");
            if (colonIndex > 0 && colonIndex < 30) {
                senderName = Component.literal(text.substring(0, colonIndex));
                content = text.substring(colonIndex + 2);
            } else {
                senderName = Component.translatable("chatsphere.system_name");
                content = text;
            }
        }

        ChatHistoryManager history = ChatHistoryManager.getInstance();

        // Detect private messages via boundChatType
        ChatType.Bound boundChatType = event.getBoundChatType();
        if (boundChatType != null) {
            var optKey = boundChatType.chatType().unwrapKey();
            if (optKey.isPresent()) {
                var key = optKey.get();
                if (key.equals(ChatType.MSG_COMMAND_INCOMING)) {
                    String convId;
                    if (mc.player != null) {
                        UUID localUuid = mc.player.getUUID();
                        convId = localUuid.compareTo(sender) < 0
                                ? localUuid + ":" + sender
                                : sender + ":" + localUuid;
                    } else {
                        convId = sender.toString();
                    }
                    history.addPrivateConversation(convId, senderName);
                    history.addMessage(senderName, sender, Component.literal(content),
                            convId, ChatMessageData.ConversationType.PRIVATE, false);
                    return;
                }
                if (key.equals(ChatType.MSG_COMMAND_OUTGOING)) {
                    return;
                }
            }
        }

        // Standard player chat goes to default channel
        history.addMessage(senderName, sender, Component.literal(content),
                ChatHistoryManager.DEFAULT_CHANNEL_ID, ChatMessageData.ConversationType.CHANNEL, false);
    }
}
