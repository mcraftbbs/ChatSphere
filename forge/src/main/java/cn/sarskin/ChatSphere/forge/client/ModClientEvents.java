package cn.sarskin.ChatSphere.forge.client;

import cn.sarskin.ChatSphere.client.ChatHintsManager;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.client.ClientHooks;
import cn.sarskin.ChatSphere.client.screen.ConfigScreen;
import cn.sarskin.ChatSphere.client.screen.ModChatScreen;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import cn.sarskin.ChatSphere.platform.PacketSender;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ModClientEvents {
    private static final List<Component> SYS_MSG_BUFFER = new ArrayList<>();
    private static UUID sysMsgSender;
    private static long sysMsgFlushTime;
    private static final long SYS_MSG_DELAY_MS = 150;

    private ModClientEvents() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ModClientEvents.class);
    }

    private static void flushSysMsgBuffer() {
        if (SYS_MSG_BUFFER.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            SYS_MSG_BUFFER.clear();
            return;
        }
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        boolean connected = mc.getConnection() != null && history.isServerConnected();

        Component combined;
        if (SYS_MSG_BUFFER.size() == 1) {
            combined = SYS_MSG_BUFFER.get(0);
        } else {
            MutableComponent merged = Component.literal("");
            for (int i = 0; i < SYS_MSG_BUFFER.size(); i++) {
                if (i > 0) merged = merged.append(Component.literal("\n"));
                merged = merged.append(SYS_MSG_BUFFER.get(i));
            }
            combined = merged;
        }

        // VoiceMessage# output goes via the dedicated voice relay.
        if (combined.getString().startsWith("VoiceMessage#")) {
            SYS_MSG_BUFFER.clear();
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
            PacketSender.toServer(ServerboundCommandMessagePayload.ID,
                    new ServerboundCommandMessagePayload(json, sendUuid, false).toBuf());
        }

        SYS_MSG_BUFFER.clear();
        sysMsgSender = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != ClientTickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChatHintsManager.getInstance().tick();

        if (!SYS_MSG_BUFFER.isEmpty() && System.currentTimeMillis() >= sysMsgFlushTime) {
            flushSysMsgBuffer();
        }

        while (mc.options.keyChat.consumeClick()) {
            mc.setScreen(new ModChatScreen(""));
        }
        if (mc.screen == null && mc.options.keyCommand.consumeClick()) {
            mc.setScreen(new ModChatScreen("/"));
        }
        while (ModKeyMappings.OPEN_CONFIG_KEY.consumeClick()) {
            mc.setScreen(new ConfigScreen());
        }
    }

    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientHooks.onClientLogin();
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        flushSysMsgBuffer();
        ClientHooks.onClientDisconnect();
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        event.setCanceled(true);
        Component message = event.getMessage();
        UUID sender = event.getSender();

        if (event.isSystem()) {
            if (event instanceof ClientChatReceivedEvent.System sys && sys.isOverlay()) return;
            if (SYS_MSG_BUFFER.isEmpty()) {
                sysMsgSender = sender != null ? sender : Util.NIL_UUID;
            }
            SYS_MSG_BUFFER.add(message);
            sysMsgFlushTime = System.currentTimeMillis() + SYS_MSG_DELAY_MS;
            return;
        }

        if (!SYS_MSG_BUFFER.isEmpty()) flushSysMsgBuffer();

        String text = message.getString();
        // Own messages (echo from server) were already added locally by ModChatScreen.sendMessage().
        if (sender == null || sender.equals(mc.player.getUUID())) return;

        Component senderName;
        String content;
        // VoiceMessage# broadcasts go through the dedicated voice relay (ChatComponentMixin).
        if (text.startsWith("VoiceMessage#")) return;

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
        // 1.20.1 ChatType.Bound lacks the type key, so vanilla /msg falls through to the default channel.
        history.addMessage(senderName, sender, Component.literal(content),
                ChatHistoryManager.DEFAULT_CHANNEL_ID, ChatMessageData.ConversationType.CHANNEL, false);
    }
}
