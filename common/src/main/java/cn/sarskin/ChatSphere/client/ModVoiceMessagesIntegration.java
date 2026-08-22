package cn.sarskin.ChatSphere.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ModVoiceMessagesIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-VM");
    private static boolean vmAvailable = false;
    private static Constructor<?> recordScreenCtor;
    private static Object playbackManager;
    private static Method playbackManagerGet;
    private static Method playbackManagerPlay;
    private static Method playbackGetDurationMs;
    private static Method formatTimeMethod;
    private static Constructor<?> playbackPlayerCtor;
    private static Method playbackPlayerSetRect;
    private static Method playbackPlayerRender;
    private static Method playbackPlayerMouseClicked;
    private static Method playbackGetAudio;
    private static Constructor<?> playbackCtor;
    private static Method playbackManagerAdd;
    private static final Set<UUID> SEEN_UUIDS = new HashSet<>();
    private static final Queue<VoiceCtx> CONTEXT_QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile PendingVoice pendingVoice;
    private static String localPlayerName;

    private record VoiceCtx(UUID senderUuid, String target) {}

    public record PendingVoice(String conversationId, String conversationType) {}

    public static void setPendingVoice(String conversationId, String conversationType) {
        pendingVoice = new PendingVoice(conversationId, conversationType);
    }

    public static PendingVoice pollPendingVoice() {
        PendingVoice v = pendingVoice;
        pendingVoice = null;
        return v;
    }

    public static void pushVoiceMessageContext(UUID senderUuid, String target) {
        CONTEXT_QUEUE.add(new VoiceCtx(senderUuid, target));
    }

    public static boolean canSendVoiceMessages() {
        if (!vmAvailable) checkPresence();
        return vmAvailable;
    }

    public static boolean isVoiceMessagesLoaded() {
        return canSendVoiceMessages();
    }

    private static void checkPresence() {
        try {
            Class.forName("ru.dimaskama.voicemessages.client.screen.RecordVoiceMessageScreen");
            vmAvailable = true;
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            Class<?> recordCls = Class.forName("ru.dimaskama.voicemessages.client.screen.RecordVoiceMessageScreen");
            recordScreenCtor = recordCls.getConstructor(Screen.class, int.class, int.class, String.class);

            Class<?> pmCls = Class.forName("ru.dimaskama.voicemessages.client.PlaybackManager");
            playbackManager = pmCls.getField("MAIN").get(null);
            playbackManagerGet = pmCls.getMethod("get", UUID.class);
            playbackManagerPlay = pmCls.getMethod("play", Class.forName("ru.dimaskama.voicemessages.client.Playback"));

            Class<?> pbCls = Class.forName("ru.dimaskama.voicemessages.client.Playback");
            playbackGetDurationMs = pbCls.getMethod("getDurationMs");
            playbackGetAudio = pbCls.getMethod("getAudio");
            playbackCtor = pbCls.getConstructor(List.class);

            Class<?> pmCls2 = Class.forName("ru.dimaskama.voicemessages.client.PlaybackManager");
            playbackManagerAdd = pmCls2.getMethod("add", pbCls);

            Class<?> ppCls = Class.forName("ru.dimaskama.voicemessages.client.PlaybackPlayer");
            playbackPlayerCtor = ppCls.getConstructor(Class.forName("ru.dimaskama.voicemessages.client.PlaybackManager"), pbCls, int.class);
            playbackPlayerSetRect = ppCls.getMethod("setRectangle", int.class, int.class, int.class, int.class);
            playbackPlayerRender = ppCls.getMethod("render", net.minecraft.client.gui.GuiGraphics.class);
            playbackPlayerMouseClicked = ppCls.getMethod("mouseClicked", int.class, int.class, int.class);
            formatTimeMethod = ppCls.getMethod("formatTime", int.class);

            // Hook PlaybackManager.playbacks to detect new voice messages
            Field pMapField = pmCls.getDeclaredField("playbacks");
            pMapField.setAccessible(true);
            Map<UUID, Object> original = (Map<UUID, Object>) pMapField.get(playbackManager);
            Map<UUID, Object> hooked = new HashMap<>(original) {
                @Override
                public Object put(UUID key, Object value) {
                    SEEN_UUIDS.add(key);
                    PendingVoice pv = pollPendingVoice();
                    if (pv != null) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null && playbackGetAudio != null) {
                            try {
                                boolean isChannel = "CHANNEL".equals(pv.conversationType());
                                List<short[]> audio = (List<short[]>) playbackGetAudio.invoke(value);
                                byte[] serialized = serializeAudio(audio);
                                // Reuse the VM playback id as voiceMessageId so all sides reference one message (no dup rows)
                                UUID voiceMessageId = key;
                                cn.sarskin.ChatSphere.network.ServerboundVoicePacket pkt =
                                    new cn.sarskin.ChatSphere.network.ServerboundVoicePacket(
                                        voiceMessageId, pv.conversationId(), pv.conversationType(),
                                        mc.player.getUUID(), audio.size(), serialized);
                                if (mc.getConnection() != null) {
                                    mc.getConnection().getConnection().send(
                                        new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(cn.sarskin.ChatSphere.network.ServerboundVoicePacket.ID, pkt.toBuf()));
                                }
                                String senderName = localPlayerName;
                                if (senderName == null) senderName = mc.player.getName().getString();
                                ChatMessageData.ConversationType ctype = isChannel
                                        ? ChatMessageData.ConversationType.CHANNEL
                                        : ChatMessageData.ConversationType.PRIVATE;
                                if (!isChannel) {
                                    ChatHistoryManager.getInstance().addPrivateConversation(pv.conversationId(),
                                            Component.literal(senderName));
                                }
                                ChatHistoryManager.getInstance().addMessage(
                                    Component.literal(senderName),
                                    mc.player.getUUID(),
                                    Component.literal("VoiceMessage#" + voiceMessageId),
                                    pv.conversationId(), ctype, true);
                                // Save to local cache using the consistent voiceMessageId
                                cn.sarskin.ChatSphere.client.ModVoiceCache.save(pv.conversationId(),
                                        pv.conversationType(), mc.player.getUUID(), voiceMessageId, serialized, audio.size());
                                // voiceMessageId == key, so the super.put(key, value) below registers it
                            } catch (Exception ignored) {}
                        }
                    }
                    return super.put(key, value);
                }
            };
            pMapField.set(playbackManager, hooked);

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) localPlayerName = mc.player.getName().getString();
        } catch (Exception e) {
            LOGGER.warn("VoiceMessages partial init failed", e);
        }
    }

    /** Ids for which an on-demand audio fetch was already sent to the server. */
    private static final Set<UUID> voiceFetchRequested = ConcurrentHashMap.newKeySet();

    /** Make sure the VoiceMessages reflection bridge is initialized (join-time packets need it). */
    public static void ensureInitialized() {
        if (!vmAvailable) checkPresence();
    }

    /** Ask the server to re-send audio for a voice message we have no cached playback for. */
    private static void requestVoiceAudio(UUID voiceMessageId) {
        if (voiceMessageId == null) return;
        if (!voiceFetchRequested.add(voiceMessageId)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.player == null) return;
        mc.getConnection().getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(
                        cn.sarskin.ChatSphere.network.ServerboundVoiceRequestPayload.ID,
                        new cn.sarskin.ChatSphere.network.ServerboundVoiceRequestPayload(voiceMessageId).toBuf()));
    }

    public static Object createPlaybackPlayer(UUID playbackUuid, int bgColor) {
        if (playbackManager == null || playbackManagerGet == null || playbackPlayerCtor == null) return null;
        try {
            Object playback = playbackManagerGet.invoke(playbackManager, playbackUuid);
            if (playback == null) {
                playback = loadFromCache(playbackUuid);
            }
            if (playback == null) {
                requestVoiceAudio(playbackUuid);
                return null;
            }
            return playbackPlayerCtor.newInstance(playbackManager, playback, bgColor);
        } catch (Exception e) {
            return null;
        }
    }

    /** SVC client API ready check — VM Playback ctor NPEs on a null api, so defer creation until ready. */
    private static boolean isVoicechatApiReady() {
        try {
            Class<?> pluginCls = Class.forName("ru.dimaskama.voicemessages.VoiceMessagesPlugin");
            java.lang.reflect.Method getClientApi = pluginCls.getMethod("getClientApi");
            return getClientApi.invoke(null) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static Object loadFromCache(UUID playbackUuid) {
        if (playbackCtor == null || playbackGetAudio == null) return null;
        if (!isVoicechatApiReady()) return null;
        byte[] audioData = ModVoiceCache.getAudioData(playbackUuid);
        Integer frameCount = ModVoiceCache.getFrameCount(playbackUuid);
        if (audioData == null || frameCount == null) return null;
        try {
            List<short[]> frames = deserializeAudio(audioData, frameCount);
            Object playback = playbackCtor.newInstance(frames);
            if (playbackManagerAdd != null) {
                playbackManagerAdd.invoke(playbackManager, playback);
            }
            return playback;
        } catch (Exception e) {
            return null;
        }
    }

    public static void setupPlaybackPlayer(Object player, int x, int y, int w, int h) {
        if (player == null || playbackPlayerSetRect == null) return;
        try {
            playbackPlayerSetRect.invoke(player, x, y, w, h);
        } catch (Exception ignored) {}
    }

    public static void renderPlaybackPlayer(Object player, Object guiGraphics) {
        if (player == null || playbackPlayerRender == null) return;
        try {
            playbackPlayerRender.invoke(player, guiGraphics);
        } catch (Exception ignored) {}
    }

    public static boolean handlePlaybackClick(Object player, int mouseX, int mouseY, int button) {
        if (player == null || playbackPlayerMouseClicked == null) return false;
        try {
            Object result = playbackPlayerMouseClicked.invoke(player, mouseX, mouseY, button);
            return result instanceof Boolean b && b;
        } catch (Exception e) { return false; }
    }

    public static void openRecordingScreen(Screen parent, int btnX, int btnY, String target) {
        if (recordScreenCtor == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            int screenW = mc.getWindow().getGuiScaledWidth();
            // VM panel spans ~243px right of leftX; clamp so buttons stay on screen.
            int maxLeftX = Math.max(0, screenW - 250);
            int x = Math.min(Math.max(btnX, 0), maxLeftX);
            Object screen = recordScreenCtor.newInstance(parent, x, btnY, target);
            mc.setScreen((Screen) screen);
        } catch (Exception e) {
            LOGGER.error("Failed to open VoiceMessages recording screen", e);
        }
    }

    public static void handleVoiceChatMessage(Component message, String tagStr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        UUID playbackUuid;
        try {
            String uuidStr = tagStr.substring("VoiceMessage#".length());
            if (uuidStr.length() == 32) {
                uuidStr = uuidStr.replaceAll("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
            }
            playbackUuid = UUID.fromString(uuidStr);
        } catch (Exception e) { return; }

        VoiceCtx ctx = pollContext();
        UUID senderUuid;
        String convId;
        ChatMessageData.ConversationType convType;
        Component name;
        boolean isOwn;

        if (ctx != null) {
            senderUuid = ctx.senderUuid;
            String target = ctx.target;
            name = lookupSenderName(ctx.senderUuid);
            if (name == null) name = message;
            convId = resolveConvId(ctx);
            convType = resolveConvType(ctx);
            isOwn = ctx.senderUuid.equals(mc.player.getUUID());
        } else {
            String msgStr = message.getString();
            String senderName;
            String targetName = null;
            int arrowIdx = msgStr.indexOf(" → ");
            if (arrowIdx >= 0) {
                senderName = msgStr.substring(0, arrowIdx);
                targetName = msgStr.substring(arrowIdx + 3);
            } else {
                senderName = msgStr;
            }
            senderUuid = resolveUuidByName(senderName);
            if (senderUuid == null) senderUuid = mc.player.getUUID();
            name = Component.literal(senderName);
            isOwn = senderUuid.equals(mc.player.getUUID());
            if (targetName != null && targetName.startsWith("#")) {
                convId = targetName.substring(1);
                convType = ChatMessageData.ConversationType.CHANNEL;
            } else if ("all".equalsIgnoreCase(targetName) || "Team".equalsIgnoreCase(targetName)) {
                convId = ChatHistoryManager.DEFAULT_CHANNEL_ID;
                convType = ChatMessageData.ConversationType.CHANNEL;
            } else if (targetName != null) {
                convId = senderUuid.toString() + ":" + mc.player.getUUID();
                ChatHistoryManager.getInstance().addPrivateConversation(convId, Component.literal(targetName));
                convType = ChatMessageData.ConversationType.PRIVATE;
            } else {
                convId = ChatHistoryManager.DEFAULT_CHANNEL_ID;
                convType = ChatMessageData.ConversationType.CHANNEL;
            }
        }

        // Chat row is created only by the server relay (handleIncomingVoice) — never insert a second row
    }

    private static UUID resolveUuidByName(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(name);
            if (info != null) return info.getProfile().getId();
        }
        return null;
    }

    public static VoiceCtx pollContext() {
        return CONTEXT_QUEUE.poll();
    }

    public static Component lookupSenderName(UUID senderUuid) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(senderUuid);
            if (info != null) {
                Component dn = info.getTabListDisplayName();
                return dn != null ? dn : Component.literal(info.getProfile().getName());
            }
        }
        return null;
    }

    public static String resolveConvId(VoiceCtx ctx) {
        if (ctx == null) return ChatHistoryManager.DEFAULT_CHANNEL_ID;
        String t = ctx.target;
        if ("all".equals(t) || "team".equals(t)) return ChatHistoryManager.DEFAULT_CHANNEL_ID;
        if (t.startsWith("#")) return t.substring(1);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            String convId = ctx.senderUuid.toString() + ":" + mc.player.getUUID();
            ChatHistoryManager.getInstance().addPrivateConversation(convId, Component.literal(t));
            return convId;
        }
        return ChatHistoryManager.DEFAULT_CHANNEL_ID;
    }

    public static ChatMessageData.ConversationType resolveConvType(VoiceCtx ctx) {
        if (ctx == null) return ChatMessageData.ConversationType.CHANNEL;
        String t = ctx.target;
        return t.startsWith("#") || "all".equals(t) || "team".equals(t)
                ? ChatMessageData.ConversationType.CHANNEL
                : ChatMessageData.ConversationType.PRIVATE;
    }

    public static void handleIncomingVoice(UUID voiceMessageId, UUID senderUuid, String conversationId,
                                             String conversationType, int frameCount, byte[] audioData) {
        ensureInitialized();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        try {
            Component name = lookupSenderName(senderUuid);
            if (name == null && mc.getConnection() != null) {
                var info = mc.getConnection().getPlayerInfo(senderUuid);
                if (info != null) name = Component.literal(info.getProfile().getName());
            }
            if (name == null) name = Component.translatable("screen.chatsphere.unknown_player");

            ChatMessageData.ConversationType ctype = "CHANNEL".equals(conversationType)
                    ? ChatMessageData.ConversationType.CHANNEL
                    : ChatMessageData.ConversationType.PRIVATE;
            if (ctype == ChatMessageData.ConversationType.PRIVATE) {
                ChatHistoryManager.getInstance().addPrivateConversation(conversationId, name);
            }

            boolean isOwn = senderUuid.equals(mc.player.getUUID());
            // Skip duplicate row when history sync already has the placeholder.
            boolean exists = ChatHistoryManager.getInstance().hasVoiceMessage(voiceMessageId);
            if (!exists) {
                ChatHistoryManager.getInstance().addMessage(
                        name, senderUuid,
                        Component.literal("VoiceMessage#" + voiceMessageId),
                        conversationId, ctype, isOwn);
            }
            // Save audio to cache regardless of VM playback availability (row stays playable later)
            cn.sarskin.ChatSphere.client.ModVoiceCache.save(conversationId,
                    conversationType, senderUuid, voiceMessageId, audioData, frameCount);

            // Playback needs the SVC client API which may not be ready yet (NPE); row + audio already persisted, skip until ready
            if (playbackCtor == null || playbackManagerAdd == null) return;
            if (!isVoicechatApiReady()) return;
            List<short[]> frames = deserializeAudio(audioData, frameCount);
            Object playback = playbackCtor.newInstance(frames);
            playbackManagerAdd.invoke(playbackManager, playback);
        } catch (Exception e) {
            LOGGER.error("Failed to handle incoming voice", e);
        }
    }

    private static UUID extractPlaybackUuid(Object playback) {
        try {
            Class<?> pmCls = Class.forName("ru.dimaskama.voicemessages.client.PlaybackManager");
            Field pMapField = pmCls.getDeclaredField("playbacks");
            pMapField.setAccessible(true);
            Map<UUID, Object> map = (Map<UUID, Object>) pMapField.get(playbackManager);
            for (Map.Entry<UUID, Object> e : map.entrySet()) {
                if (e.getValue() == playback) return e.getKey();
            }
        } catch (Exception ignored) {}
        return UUID.randomUUID();
    }

    public static byte[] serializeAudio(List<short[]> frames) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        for (short[] frame : frames) {
            dos.writeInt(frame.length);
            for (short s : frame) dos.writeShort(s);
        }
        return baos.toByteArray();
    }

    public static List<short[]> deserializeAudio(byte[] data, int frameCount) throws Exception {
        if (data == null || frameCount < 0 || frameCount > data.length / 4) {
            throw new IllegalArgumentException("Invalid audio payload: data="
                    + (data == null ? "null" : data.length) + " frames=" + frameCount);
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);
        List<short[]> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int len = dis.readInt();
            int remaining = dis.available();
            if (len < 0 || len > remaining / 2 || len > 1_000_000) {
                throw new IllegalArgumentException("Invalid audio frame length: " + len);
            }
            short[] frame = new short[len];
            for (int j = 0; j < len; j++) frame[j] = dis.readShort();
            frames.add(frame);
        }
        return frames;
    }
}
