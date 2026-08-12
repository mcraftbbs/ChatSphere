package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.config.CfgValue;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import cn.sarskin.ChatSphere.server.ModVoiceStorage;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-side payload handling, decoupled from the payload records so that
 * servers never load client-only classes (Fabric's environment checker rejects
 * client class references in server-loaded classes).
 */
public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {}

    public static void channelAction(Player player, ServerboundChannelActionPayload p) {
        var server = player.getServer();
        if (server == null) return;
        UUID realUuid = player.getUUID();
        ModServerChannels msc = ModServerChannels.getInstance(server);
        switch (p.action()) {
            case CREATE -> msc.createChannel(p.channelId(), realUuid, p.isPublic(), p.showInExplore(), p.mainChatEnabled(), p.defaultSubChannel());
            case UPDATE_CONFIG -> msc.updateChannelConfig(p.channelId(), p.isPublic(), p.description(), p.displayName(),
                    p.admins(), p.mutedPlayers(), p.invitedPlayers(), p.inviteCode(), realUuid,
                    p.showInExplore(), p.mainChatEnabled(), p.defaultSubChannel());
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
                    sp.connection.send(new ClientboundCustomPayloadPacket(ClientboundPublicChannelListPayload.ID, new ClientboundPublicChannelListPayload(publicList).toBuf()));
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
            String chatTarget = msc.resolveChatChannel(chatChannelId);
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
        msc.addChatMessage(senderName, realUuid, p.description(), chatChannelId, convType,
                p.replyContent(), p.replySender(), sanitizeItemNbt(p.itemNbt()));
        long now = System.currentTimeMillis();
        ClientboundChatPayload relay = new ClientboundChatPayload(
                new ClientboundChatPayload.StoredMessage(senderName, realUuid, p.description(), now,
                        chatChannelId, convType, p.replyContent(), p.replySender(), sanitizeItemNbt(p.itemNbt())));

        if (targetUuid != null) {
            ServerPlayer target = server.getPlayerList().getPlayer(targetUuid);
            if (target != null) {
                target.connection.send(new ClientboundCustomPayloadPacket(ClientboundChatPayload.ID, relay.toBuf()));
            }
        } else {
            List<String> recipients = msc.effectiveMembers(chatChannelId);
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                if (recipients.contains(other.getUUID().toString())
                        && !other.getUUID().equals(realUuid)) {
                    other.connection.send(new ClientboundCustomPayloadPacket(ClientboundChatPayload.ID, relay.toBuf()));
                }
            }
        }
    }

    /** Cap client-supplied item NBT (base64). */
    private static final int MAX_ITEM_NBT_BASE64 = 16 * 1024;

    private static String sanitizeItemNbt(String itemNbt) {
        return itemNbt != null && itemNbt.length() <= MAX_ITEM_NBT_BASE64 ? itemNbt : "";
    }

    public static void commandMessage(Player player, ServerboundCommandMessagePayload p) {
        if (player == null) return;
        var server = player.getServer();
        if (server == null) return;
        ModServerChannels msc = ModServerChannels.getInstance(server);
        String name = player.getName().getString();
        UUID sent = p.senderUuid();
        // Accept only own uuid or NIL; anything else is a forged injection.
        UUID suid;
        if (sent != null && (sent.equals(player.getUUID()) || sent.equals(Util.NIL_UUID))) {
            suid = sent;
        } else {
            return;
        }
        msc.addCommandMessage(name, suid, p.content());
    }

    public static void permissionCheck(Player player, ServerboundPermissionCheckPayload p) {
        if (player instanceof ServerPlayer sp) {
            boolean allowed = sp.hasPermissions(2);
            sp.connection.send(new ClientboundCustomPayloadPacket(ClientboundPermissionResponsePayload.ID, new ClientboundPermissionResponsePayload(p.scope(), allowed).toBuf()));
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

            msc.addChatMessage(senderName, realUuid,
                    "VoiceMessage#" + p.voiceMessageId(),
                    p.conversationId(), p.conversationType(), "", "", "");

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
                    target.connection.send(new ClientboundCustomPayloadPacket(ClientboundVoicePacket.ID, relay.toBuf()));
                } else {
                    storage.store(p.voiceMessageId(), senderStr, p.conversationId(), p.conversationType(), p.frameCount(), p.audioData());
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

            msc.addChatMessage(senderName, realUuid,
                    "VoiceMessage#" + p.voiceMessageId(),
                    p.conversationId(), p.conversationType(), "", "", "");

            ServerPlayer target = server.getPlayerList().getPlayer(recipientUuid);
            if (target != null) {
                target.connection.send(new ClientboundCustomPayloadPacket(ClientboundVoicePacket.ID, relay.toBuf()));
            } else {
                storage.store(p.voiceMessageId(), senderStr, p.conversationId(), p.conversationType(), p.frameCount(), p.audioData());
            }
        }
    }
}
