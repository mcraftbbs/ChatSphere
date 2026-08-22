package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.config.CfgValue;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import cn.sarskin.ChatSphere.server.ModServerEmoji;
import cn.sarskin.ChatSphere.server.ModVoiceStorage;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            case JOIN_MEMBER -> msc.addMemberToChannel(p.channelId(), realUuid.toString());
            case JOIN_BY_CODE -> {
                if (p.inviteCode() != null && !p.inviteCode().isEmpty()) {
                    String result = msc.joinByCode(p.inviteCode(), realUuid);
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
                    sp.connection.send(new ClientboundCustomPayloadPacket(
                            new ClientboundPublicChannelListPayload(publicList)));
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
                p.replyContent(), p.replySender(), sanitizeItemNbt(p.itemNbt()));
        if (convType.equals("CHANNEL") && chatTarget != null) {
            msc.recordSlowModeMessage(chatTarget, realUuid.toString());
        }
        ClientboundChatPayload relay = new ClientboundChatPayload(stored);

        if (targetUuid != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
            if (target != null) {
                target.connection.send(new ClientboundCustomPayloadPacket(relay));
            }
        } else {
            List<String> recipients = msc.effectiveMembers(chatChannelId);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (recipients.contains(other.getUUID().toString())
                        && !other.getUUID().equals(realUuid)) {
                    other.connection.send(new ClientboundCustomPayloadPacket(relay));
                }
            }
        }
    }

    /** Cap client-supplied item NBT (base64). */
    private static final int MAX_ITEM_NBT_BASE64 = 16 * 1024;

    private static String sanitizeItemNbt(String itemNbt) {
        return itemNbt != null && itemNbt.length() <= MAX_ITEM_NBT_BASE64 ? itemNbt : "";
    }

    /** True if a voice placeholder with this id is already in the server history. */
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
            sp.connection.send(new ClientboundCustomPayloadPacket(
                    new ClientboundPermissionResponsePayload(p.scope(), allowed)));
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
            ClientboundConfigSyncPayload sync = new ClientboundConfigSyncPayload(Map.of(p.key(), p.value()));
            for (ServerPlayer target : sp.server.getPlayerList().getPlayers()) {
                target.connection.send(new ClientboundCustomPayloadPacket(sync));
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

            // Multiple recipients may upload the same voice; only record/relay it once.
            boolean first = !voicePlaceholderExists(msc, p.voiceMessageId());
            if (first) {
                msc.addChatMessage(senderName, realUuid,
                        "VoiceMessage#" + p.voiceMessageId(),
                        p.conversationId(), p.conversationType(), "", "", "");
            }
            // Keep a server copy for late joiners (no-op when offline storage is off).
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
                    target.connection.send(new ClientboundCustomPayloadPacket(relay));
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
                target.connection.send(new ClientboundCustomPayloadPacket(relay));
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
            sp.connection.send(new ClientboundCustomPayloadPacket(relay));
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
                Component err = store.add(p.channelId(), p.name(), p.data());
                if (err != null) {
                    sp.sendSystemMessage(err, false);
                    return;
                }
                store.recordUpload(player.getUUID());
                store.broadcastAdd(p.channelId(), p.name(), p.data());
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
