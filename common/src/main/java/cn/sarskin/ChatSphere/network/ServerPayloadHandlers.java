package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.client.image.ChatImageGuard;
import cn.sarskin.ChatSphere.config.CfgValue;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.platform.PacketSender;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import cn.sarskin.ChatSphere.server.ModServerEmoji;
import cn.sarskin.ChatSphere.server.ModServerImages;
import cn.sarskin.ChatSphere.server.ModVoiceStorage;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decoupled from payload records so servers never load client-only classes (Fabric env checker rejects them). */
public final class ServerPayloadHandlers {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-ServerNet");

    private ServerPayloadHandlers() {}

    public static void channelAction(Player player, ServerboundChannelActionPayload p) {
        var server = player.getServer();
        if (server == null) return;
        UUID realUuid = player.getUUID();
        ModServerChannels msc = ModServerChannels.getInstance(server);
        switch (p.action()) {
            case CREATE -> msc.createChannel(p.channelId(), realUuid, p.isPublic(), p.showInExplore(), p.mainChatEnabled(), p.defaultSubChannel(), p.slowModeSeconds());
            case UPDATE_CONFIG -> msc.updateChannelConfig(p.channelId(), p.isPublic(), p.description(), p.displayName(),
                    p.admins(), p.mutedPlayers(), p.invitedPlayers(), p.inviteCode(), realUuid,
                    p.showInExplore(), p.mainChatEnabled(), p.defaultSubChannel(), p.slowModeSeconds());
            case JOIN_MEMBER -> {
                if (p.channelId() != null && !p.channelId().isEmpty()) {
                    msc.addMemberToChannel(p.channelId(), realUuid.toString());
                    msc.sendChannelHistoryToOnlinePlayer(p.channelId(), realUuid.toString());
                }
            }
            case JOIN_BY_CODE -> {
                if (p.inviteCode() != null && !p.inviteCode().isEmpty()) {
                    String result = msc.joinByCode(p.inviteCode(), realUuid);
                    if ("success".equals(result)) {
                        msc.sendChannelHistoryToOnlinePlayer(msc.channelIdForInviteCode(p.inviteCode()), realUuid.toString());
                    }
                    if (player instanceof ServerPlayer sp) {
                        Component msg = switch (result) {
                            case "already_member" -> Component.translatable(
                                    "screen.chatsphere.join_channel.result_already_member");
                            case "not_found" -> Component.translatable(
                                    "screen.chatsphere.join_channel.result_not_found", p.inviteCode());
                            default -> Component.translatable(
                                    "screen.chatsphere.join_channel.result_success");
                        };
                        sp.sendSystemMessage(msg, false);
                    }
                }
            }
            case SEND_CHAT -> sendChat(server, player, realUuid, msc, p);
            case REMOVE_CHANNEL -> msc.removeChannel(p.channelId(), realUuid);
            case TOGGLE_MUTE -> {
                if (!p.description().isEmpty() && !p.channelId().isEmpty()) {
                    msc.toggleMute(p.channelId(), p.description(), realUuid);
                }
            }
            case TOGGLE_ADMIN -> {
                if (!p.description().isEmpty() && !p.channelId().isEmpty()) {
                    msc.toggleAdmin(p.channelId(), p.description(), realUuid);
                }
            }
            case TOGGLE_INVITE -> {
                if (!p.description().isEmpty() && !p.channelId().isEmpty()) {
                    msc.toggleInvite(p.channelId(), p.description(), realUuid);
                }
            }
            case KICK_MEMBER -> {
                if (!p.description().isEmpty() && !p.channelId().isEmpty()) {
                    msc.kickMember(p.channelId(), p.description(), realUuid);
                }
            }
            case LEAVE_CHANNEL -> msc.leaveChannel(p.channelId(), realUuid);
            case LIST_PUBLIC -> {
                if (player instanceof ServerPlayer sp) {
                    var publicList = msc.getPublicChannels();
                    PacketSender.toPlayer(sp, ClientboundPublicChannelListPayload.ID, new ClientboundPublicChannelListPayload(publicList));
                }
            }
            case CREATE_VOICE_ROOM -> {
                if (!p.description().isEmpty()) {
                    msc.createVoiceRoom(p.channelId(), p.description(), realUuid);
                }
            }
            case DELETE_VOICE_ROOM -> {
                if (!p.description().isEmpty()) {
                    msc.deleteVoiceRoom(p.channelId(), p.description(), realUuid);
                }
            }
            case JOIN_VOICE_ROOM -> {
                if (!p.description().isEmpty()) {
                    msc.joinVoiceRoom(p.channelId(), p.description(), realUuid);
                }
            }
            case LEAVE_VOICE_ROOM -> {
                if (!p.description().isEmpty()) {
                    msc.leaveVoiceRoom(p.channelId(), p.description(), realUuid);
                }
            }
            case RENAME_SUBCHANNEL -> {
                if (!p.channelId().isEmpty() && !p.displayName().isEmpty()) {
                    msc.renameChannel(p.channelId(), p.displayName(), realUuid);
                }
            }
            case REORDER_CHANNEL -> {
                if (!p.description().isEmpty()) {
                    List<String> ids = new ArrayList<>();
                    for (String s : p.description().split(",")) {
                        if (!s.trim().isEmpty()) ids.add(s.trim());
                    }
                    msc.reorderChannels(ids, realUuid);
                }
            }
            case MOVE_CHANNEL -> {
                if (!p.channelId().isEmpty() && !p.description().isEmpty()) {
                    msc.moveChannel(p.channelId(), p.description(), realUuid);
                }
            }
        }
    }

    private static void sendChat(MinecraftServer server, Player player, UUID realUuid,
                                 ModServerChannels msc, ServerboundChannelActionPayload p) {
        if (p.channelId().isEmpty() || p.description().isEmpty()) return;
        String senderName = player.getName().getString();
        String convType;
        String chatChannelId = p.channelId();
        UUID targetUuid = null;
        boolean muted = false;
        boolean notMember = false;
        String chatTarget = null;

        if (chatChannelId.contains(":")) {
            convType = "PRIVATE";
            String senderStr = realUuid.toString();
            String[] parts = chatChannelId.split(":");
            String otherStr = parts[0].equals(senderStr) ? parts[1] : parts[0];
            try {
                targetUuid = UUID.fromString(otherStr);
            } catch (Exception ignored) {
            }
        } else {
            convType = "CHANNEL";
            chatTarget = msc.resolveChatChannel(chatChannelId);
            if (chatTarget == null) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable("chatsphere.chat_disabled.feedback"), false);
                }
                return;
            }
            var entry = msc.getChannel(chatTarget);
            if (entry == null || !msc.effectiveMembers(chatTarget).contains(realUuid.toString())) {
                notMember = true;
            } else if (msc.isMuted(chatTarget, realUuid.toString())) {
                muted = true;
            }
            chatChannelId = chatTarget;
        }
        if (notMember) return;
        if (muted) {
            if (player instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("chatsphere.mute.feedback"), false);
            }
            return;
        }

        // Slow mode (skipped for private conversations).
        if (convType.equals("CHANNEL") && chatTarget != null) {
            long remaining = msc.slowModeRemainingMillis(chatTarget, realUuid.toString());
            if (remaining > 0) {
                if (player instanceof ServerPlayer sp) {
                    sp.sendSystemMessage(Component.translatable(
                            "chatsphere.slowmode.feedback", (remaining + 999) / 1000), false);
                }
                return;
            }
        }

        String bannedRaw = ModServerConfig.CONFIG.bannedWords.get();
        if (!bannedRaw.isEmpty()) {
            String[] patterns = bannedRaw.split("\n");
            for (String pattern : patterns) {
                pattern = pattern.trim();
                if (pattern.isEmpty()) continue;
                try {
                    if (java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(p.description()).find()) {
                        if (player instanceof ServerPlayer sp) {
                            sp.sendSystemMessage(Component.translatable("chatsphere.banned_word.feedback"), false);
                        }
                        return;
                    }
                } catch (java.util.regex.PatternSyntaxException ignored) {
                }
            }
        }
        ClientboundMessageSyncPayload.StoredMessage stored = msc.addChatMessage(senderName, realUuid, p.description(), chatChannelId, convType,
                p.replyContent(), p.replySender(), sanitizeItemNbt(p.itemNbt()), false);
        if (convType.equals("CHANNEL") && chatTarget != null) {
            msc.recordSlowModeMessage(chatTarget, realUuid.toString());
        }
        ClientboundChatPayload relay = new ClientboundChatPayload(
                new ClientboundChatPayload.StoredMessage(stored.senderName(), stored.senderUuid(), stored.content(),
                        stored.timestamp(), stored.conversationId(), stored.conversationType(),
                        stored.replyContent(), stored.replySender(), stored.itemNbt(), stored.messageId(), stored.isInput()));

        if (targetUuid != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
            if (target != null) {
                PacketSender.toPlayer(target, ClientboundChatPayload.ID, relay.toBuf());
            }
        } else {
            List<String> recipients = msc.effectiveMembers(chatChannelId);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (recipients.contains(other.getUUID().toString())
                        && !other.getUUID().equals(realUuid)) {
                    PacketSender.toPlayer(other, ClientboundChatPayload.ID, relay.toBuf());
                }
            }
        }
    }

    /** Cap client-supplied item NBT (base64). */
    private static final int MAX_ITEM_NBT_BASE64 = 16 * 1024;

    private static String sanitizeItemNbt(String itemNbt) {
        return itemNbt != null && itemNbt.length() <= MAX_ITEM_NBT_BASE64 ? itemNbt : "";
    }

    /** True when a voice placeholder with this id is already stored. */
    private static boolean voicePlaceholderExists(ModServerChannels msc, UUID voiceMessageId) {
        return voiceMessageId != null && msc.hasMessageContent("VoiceMessage#" + voiceMessageId);
    }

    public static void commandMessage(Player player, ServerboundCommandMessagePayload p) {
        if (player == null) return;
        var server = player.getServer();
        if (server == null) return;
        ModServerChannels msc = ModServerChannels.getInstance(server);
        String name = player.getName().getString();
        UUID sent = p.senderUuid();
        // Console history is per-player: NIL (legacy clients) falls back to the sender's own UUID.
        UUID suid;
        if (sent != null && sent.equals(player.getUUID())) {
            suid = sent;
        } else if (sent != null && sent.equals(Util.NIL_UUID)) {
            suid = player.getUUID();
        } else {
            return;
        }
        msc.addCommandMessage(name, suid, p.content(), p.isInput());
    }

    public static void permissionCheck(Player player, ServerboundPermissionCheckPayload p) {
        if (player instanceof ServerPlayer sp) {
            boolean allowed = sp.hasPermissions(2);
            PacketSender.toPlayer(sp, ClientboundPermissionResponsePayload.ID, new ClientboundPermissionResponsePayload(p.scope(), allowed));
        }
    }

    public static void configUpdate(Player player, ServerboundConfigUpdatePayload p) {
        if (!(player instanceof ServerPlayer sp) || !sp.hasPermissions(2)) return;
        try {
            Field field = ModServerConfig.class.getField(p.key());
            Object cfg = ModServerConfig.CONFIG;
            Object val = field.get(cfg);
            if (val instanceof CfgValue.Bool bv) {
                bv.set(Boolean.parseBoolean(p.value()));
            } else if (val instanceof CfgValue.Int iv) {
                int v;
                try {
                    v = Integer.parseInt(p.value());
                } catch (NumberFormatException e) {
                    return;
                }
                if (v < 0 || v > 1_000_000) return;
                iv.set(v);
            } else if (val instanceof CfgValue.Str sv) {
                sv.set(p.value());
            }
            ModServerConfig.CONFIG_SPEC.save();
            // Credentials are write-only: never echo them back to clients
            if (ModServerConfig.isSecret(p.key())) return;
            ClientboundConfigSyncPayload sync = new ClientboundConfigSyncPayload(Map.of(p.key(), p.value()));
            for (ServerPlayer target : sp.server.getPlayerList().getPlayers()) {
                PacketSender.toPlayer(target, ClientboundConfigSyncPayload.ID, sync.toBuf());
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("ConfigUpdate").warn("Failed to apply config {}={}", p.key(), p.value(), e);
        }
    }

    public static void voicePacket(Player player, ServerboundVoicePacket p) {
        var server = player.getServer();
        if (server == null) return;

        UUID realUuid = player.getUUID();
        String senderStr = realUuid.toString();
        ClientboundVoicePacket relay = new ClientboundVoicePacket(
                p.voiceMessageId(), realUuid, p.conversationId(), p.conversationType(), p.frameCount(), p.audioData());
        ModVoiceStorage storage = ModVoiceStorage.getInstance(server);
        ModServerChannels msc = ModServerChannels.getInstance(server);
        String senderName = player.getName().getString();

        if ("CHANNEL".equals(p.conversationType())) {
            if (p.conversationId() == null || p.conversationId().isEmpty()) return;
            List<String> recipients = msc.effectiveMembers(p.conversationId());
            if (!recipients.contains(senderStr)) return;
            if (msc.isMuted(p.conversationId(), senderStr)) return;

            // Several recipients may upload the same voice; record and relay it once.
            boolean first = !voicePlaceholderExists(msc, p.voiceMessageId());
            if (first) {
                msc.addChatMessage(senderName, realUuid,
                        "VoiceMessage#" + p.voiceMessageId(),
                        p.conversationId(), p.conversationType(), "", "", "");
            }
            // Keep a copy for late joiners; a no-op when offline storage is off.
            storage.store(p.voiceMessageId(), senderStr, p.conversationId(), p.conversationType(), p.frameCount(), p.audioData());
            if (!first) return;

            for (String memberUuid : recipients) {
                if (memberUuid.equals(senderStr)) continue;
                UUID targetUuid;
                try {
                    targetUuid = UUID.fromString(memberUuid);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
                if (target != null) {
                    PacketSender.toPlayer(target, ClientboundVoicePacket.ID, relay.toBuf());
                }
            }
        } else if ("PRIVATE".equals(p.conversationType()) && p.conversationId() != null && p.conversationId().contains(":")) {
            String[] parts = p.conversationId().split(":");
            if (parts.length != 2) return;
            UUID recipientUuid;
            try {
                recipientUuid = UUID.fromString(parts[0].equals(senderStr) ? parts[1] : parts[0]);
            } catch (IllegalArgumentException e) {
                return;
            }
            if (recipientUuid.equals(realUuid)) return;

            boolean first = !voicePlaceholderExists(msc, p.voiceMessageId());
            if (first) {
                msc.addChatMessage(senderName, realUuid,
                        "VoiceMessage#" + p.voiceMessageId(),
                        p.conversationId(), p.conversationType(), "", "", "");
            }
            storage.store(p.voiceMessageId(), senderStr, p.conversationId(), p.conversationType(), p.frameCount(), p.audioData());
            if (!first) return;

            ServerPlayer target = server.getPlayerList().getPlayer(recipientUuid);
            if (target != null) {
                PacketSender.toPlayer(target, ClientboundVoicePacket.ID, relay.toBuf());
            }
        }
    }

    /** On-demand voice fetch: re-send stored audio for a voice message the client lacks. */
    public static void voiceRequest(Player player, ServerboundVoiceRequestPayload p) {
        var server = player.getServer();
        if (server == null) return;
        if (p.voiceMessageId() == null) return;
        ModVoiceStorage storage = ModVoiceStorage.getInstance(server);
        ModVoiceStorage.StoredVoice sv = storage.findById(p.voiceMessageId());
        if (sv == null) return;
        UUID sender;
        try {
            sender = UUID.fromString(sv.senderUuid());
        } catch (IllegalArgumentException e) {
            return;
        }
        ClientboundVoicePacket relay = new ClientboundVoicePacket(
                sv.voiceMessageId(), sender, sv.conversationId(), sv.conversationType(),
                sv.frameCount(), sv.audioData());
        if (player instanceof ServerPlayer sp) {
            PacketSender.toPlayer(sp, ClientboundVoicePacket.ID, relay.toBuf());
        }
    }

    /** Emoji actions: all inputs hostile — validated by EmojiFileGuard, gated by config + cooldown, per-folder capped. */
    public static void customEmoji(Player player, ServerboundCustomEmojiPayload p) {
        var server = player.getServer();
        if (server == null) return;
        if (!ModServerConfig.CONFIG.emojiSharingEnabled.get()) return;
        if (!(player instanceof ServerPlayer sp)) return;

        switch (p.action()) {
            case SYNC_REQUEST -> {
                ModServerEmoji.getInstance(server).syncTo(sp);
            }
            case ADD -> {
                // Assemble first: a rejection must be reported once, not once per chunk.
                byte[] data = p.total() > 1 ? assembleEmoji(player.getUUID(), p) : p.data();
                if (data == null) return;
                if (!canUpload(player, p.channelId())) {
                    sp.sendSystemMessage(Component.translatable("chatsphere.emoji.no_permission"), false);
                    return;
                }
                if (!validChannelTarget(sp, p.channelId())) return;
                ModServerEmoji store = ModServerEmoji.getInstance(server);
                long cooldown = store.uploadCooldownRemaining(player.getUUID());
                if (cooldown > 0) {
                    sp.sendSystemMessage(Component.translatable(
                            "chatsphere.emoji.cooldown", (cooldown + 999) / 1000), false);
                    return;
                }
                Component err = store.add(p.channelId(), p.name(), data);
                if (err != null) {
                    sp.sendSystemMessage(err, false);
                    return;
                }
                store.recordUpload(player.getUUID());
                store.broadcastAdd(p.channelId(), p.name(), data);
                sp.sendSystemMessage(Component.translatable(
                        "chatsphere.emoji.uploaded", p.name()), false);
                LOGGER.info("{} uploaded server emoji :{}: to '{}'", player.getName().getString(), p.name(), p.channelId());
            }
            case DELETE -> {
                if (!canUpload(player, p.channelId())) {
                    sp.sendSystemMessage(Component.translatable("chatsphere.emoji.no_permission"), false);
                    return;
                }
                // deleted channels may still be cleaned up
                ModServerEmoji store = ModServerEmoji.getInstance(server);
                Component err = store.delete(p.channelId(), p.name());
                if (err != null) {
                    sp.sendSystemMessage(err, false);
                    return;
                }
                store.broadcastDelete(p.channelId(), p.name());
            }
        }
    }

    /** Relay a typing ping to the other members; never stored, only throttled. */
    public static void typing(Player player, ServerboundTypingPayload p) {
        var server = player.getServer();
        if (server == null) return;
        ModServerChannels msc = ModServerChannels.getInstance(server);
        String senderUuid = player.getUUID().toString();
        String convId = p.conversationId();
        String convType = p.conversationType();
        if (convId == null || convId.isEmpty()) return;

        List<UUID> targets = new ArrayList<>();
        if ("CHANNEL".equals(convType)) {
            String target = msc.resolveChatChannel(convId);
            if (target == null) return;
            if (!msc.effectiveMembers(target).contains(senderUuid)) return;
            if (msc.isMuted(target, senderUuid)) return;
            convId = target;
            for (String member : msc.effectiveMembers(target)) {
                UUID uuid = parseUuid(member);
                if (uuid != null && !uuid.equals(player.getUUID())) targets.add(uuid);
            }
        } else if ("PRIVATE".equals(convType) && convId.contains(":")) {
            String[] parts = convId.split(":");
            if (parts.length != 2) return;
            UUID other = parseUuid(parts[0].equals(senderUuid) ? parts[1] : parts[0]);
            if (other == null || other.equals(player.getUUID())) return;
            targets.add(other);
        } else {
            return;
        }
        if (targets.isEmpty() || !msc.acceptTyping(convId, senderUuid)) return;

        ClientboundTypingPayload relay = new ClientboundTypingPayload(
                player.getUUID(), player.getName().getString(), convId, convType);
        for (UUID uuid : targets) {
            ServerPlayer target = server.getPlayerList().getPlayer(uuid);
            if (target != null) {
                PacketSender.toPlayer(target, ClientboundTypingPayload.ID, relay.toBuf());
            }
        }
    }

    /** Chat images: requests replay stored bytes, uploads are gated by config, permission, cooldown and cap. */
    public static void chatImage(Player player, ServerboundChatImagePayload p) {
        var server = player.getServer();
        if (server == null) return;
        if (!(player instanceof ServerPlayer sp)) return;
        ModServerImages store = ModServerImages.getInstance(server);
        if (!ModServerConfig.CONFIG.chatImageEnabled.get()) {
            ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.failed(p.id(), ChatImageGuard.ERR_DISABLED));
            return;
        }
        switch (p.action()) {
            case REQUEST -> {
                byte[] data = store.load(p.id());
                if (data == null || data.length == 0) {
                    ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.failed(p.id(), ChatImageGuard.ERR_FORMAT));
                    return;
                }
                int count = (data.length + ModServerImages.CHUNK_BYTES - 1) / ModServerImages.CHUNK_BYTES;
                if (count > ModServerImages.MAX_CHUNKS) {
                    ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.failed(p.id(), ChatImageGuard.ERR_SIZE));
                    return;
                }
                for (int i = 0; i < count; i++) {
                    int from = i * ModServerImages.CHUNK_BYTES;
                    int to = Math.min(data.length, from + ModServerImages.CHUNK_BYTES);
                    ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.data(
                            p.id(), i, count, java.util.Arrays.copyOfRange(data, from, to)));
                }
            }
            case UPLOAD -> {
                if (ModServerConfig.CONFIG.chatImageUploadRequiresOp.get() && !player.hasPermissions(2)) {
                    ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.failed(p.id(), "chatsphere.image.err_op"));
                    return;
                }
                // Assemble first: a rejection must be reported once, not once per chunk.
                byte[] data = p.partCount() > 1 ? assembleImage(sp.getUUID(), p) : p.data();
                if (data == null) return;
                long cooldown = store.uploadCooldownRemaining(sp.getUUID());
                if (cooldown > 0) {
                    ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.failed(p.id(), "chatsphere.image.err_cooldown"));
                    return;
                }
                int cap = ModServerConfig.CONFIG.chatImageMaxPerPlayer.get();
                if (cap > 0 && store.countFor(sp.getUUID()) >= cap) {
                    ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.failed(p.id(), "chatsphere.image.err_limit"));
                    return;
                }
                int[] dims = new int[2];
                String error = store.store(p.id(), data, p.sourceUrl(), dims);
                if (error != null) {
                    ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.failed(p.id(), error));
                    return;
                }
                store.recordUpload(sp.getUUID());
                ClientboundChatImagePayload.sendTo(sp, ClientboundChatImagePayload.accepted(p.id(), dims[0], dims[1]));
                LOGGER.info("{} uploaded chat image {}", player.getName().getString(), p.id());
            }
        }
    }

    /** Chunks of one upload in flight, keyed by player. */
    private static final Map<UUID, PendingImage> PENDING_IMAGES = new ConcurrentHashMap<>();
    private static final long PENDING_IMAGE_TIMEOUT_MS = 30_000L;

    private static final class PendingImage {
        private final String sourceUrl;
        private final byte[][] parts;
        private final long startedAt = System.currentTimeMillis();
        private int received;

        private PendingImage(String sourceUrl, int total) {
            this.sourceUrl = sourceUrl == null ? "" : sourceUrl;
            this.parts = new byte[total][];
        }
    }

    /** Whole image once every chunk arrived, else null. */
    private static byte[] assembleImage(UUID playerUuid, ServerboundChatImagePayload p) {
        PendingImage pending = PENDING_IMAGES.get(playerUuid);
        if (p.partIndex() == 0 || pending == null || pending.parts.length != p.partCount()
                || !pending.sourceUrl.equals(p.sourceUrl() == null ? "" : p.sourceUrl())) {
            pending = new PendingImage(p.sourceUrl(), p.partCount());
            PENDING_IMAGES.put(playerUuid, pending);
        }
        if (pending.parts[p.partIndex()] == null) {
            pending.parts[p.partIndex()] = p.data();
            pending.received++;
        }
        if (pending.received < pending.parts.length) {
            if (System.currentTimeMillis() - pending.startedAt > PENDING_IMAGE_TIMEOUT_MS) {
                PENDING_IMAGES.remove(playerUuid, pending);
            }
            return null;
        }
        PENDING_IMAGES.remove(playerUuid, pending);
        int size = 0;
        for (byte[] part : pending.parts) size += part.length;
        byte[] full = new byte[size];
        int offset = 0;
        for (byte[] part : pending.parts) {
            System.arraycopy(part, 0, full, offset, part.length);
            offset += part.length;
        }
        return full;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Chunks of one upload in flight, keyed by player. */
    private static final Map<UUID, PendingEmoji> PENDING_EMOJI = new ConcurrentHashMap<>();
    private static final long PENDING_EMOJI_TIMEOUT_MS = 30_000L;

    private static final class PendingEmoji {
        private final String name;
        private final String channelId;
        private final byte[][] parts;
        private final long startedAt = System.currentTimeMillis();
        private int received;

        private PendingEmoji(String name, String channelId, int total) {
            this.name = name;
            this.channelId = channelId;
            this.parts = new byte[total][];
        }
    }

    /** Whole image once every chunk arrived, else null. */
    private static byte[] assembleEmoji(UUID playerUuid, ServerboundCustomEmojiPayload p) {
        PendingEmoji pending = PENDING_EMOJI.get(playerUuid);
        if (p.index() == 0 || pending == null || !pending.name.equals(p.name())
                || !pending.channelId.equals(p.channelId()) || pending.parts.length != p.total()) {
            pending = new PendingEmoji(p.name(), p.channelId(), p.total());
            PENDING_EMOJI.put(playerUuid, pending);
        }
        if (pending.parts[p.index()] == null) {
            pending.parts[p.index()] = p.data();
            pending.received++;
        }
        if (pending.received < pending.parts.length) {
            if (System.currentTimeMillis() - pending.startedAt > PENDING_EMOJI_TIMEOUT_MS) {
                PENDING_EMOJI.remove(playerUuid, pending);
            }
            return null;
        }
        PENDING_EMOJI.remove(playerUuid, pending);
        int size = 0;
        for (byte[] part : pending.parts) size += part.length;
        byte[] full = new byte[size];
        int offset = 0;
        for (byte[] part : pending.parts) {
            System.arraycopy(part, 0, full, offset, part.length);
            offset += part.length;
        }
        return full;
    }

    /** Public uploads keep the OP/switch gate; any player may upload to a channel they can see. */
    private static boolean canUpload(Player player, String channelId) {
        return (channelId != null && !channelId.isEmpty())
                || !ModServerConfig.CONFIG.emojiUploadRequiresOp.get()
                || player.hasPermissions(2);
    }

    /** Channel targets must exist; public ("") always valid. */
    private static boolean validChannelTarget(ServerPlayer sp, String channelId) {
        if (channelId == null || channelId.isEmpty()) return true;
        if (ModServerChannels.getInstance(sp.server).getChannel(channelId) != null) return true;
        sp.sendSystemMessage(Component.translatable("chatsphere.emoji.err_channel"), false);
        return false;
    }
}
