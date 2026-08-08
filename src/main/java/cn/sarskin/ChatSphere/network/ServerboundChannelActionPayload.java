package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ServerboundChannelActionPayload(
        Action action,
        String channelId,
        UUID ownerUuid,
        boolean isPublic,
        String description,
        String displayName,
        List<String> admins,
        List<String> mutedPlayers,
        List<String> invitedPlayers,
        String inviteCode,
        boolean showInExplore,
        String replyContent,
        String replySender,
        String itemNbt,
        boolean mainChatEnabled,
        String defaultSubChannel
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundChannelActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "channel_action"));

    public static final StreamCodec<ByteBuf, ServerboundChannelActionPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundChannelActionPayload::write, ServerboundChannelActionPayload::read);

    private static void write(ByteBuf buf, ServerboundChannelActionPayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.channelId);
        writeUtf(buf, p.ownerUuid.toString());
        buf.writeBoolean(p.isPublic);
        writeUtf(buf, p.description);
        writeUtf(buf, p.displayName);
        writeStringList(buf, p.admins);
        writeStringList(buf, p.mutedPlayers);
        writeStringList(buf, p.invitedPlayers);
        writeUtf(buf, p.inviteCode);
        buf.writeBoolean(p.showInExplore);
        writeUtf(buf, p.replyContent);
        writeUtf(buf, p.replySender);
        writeUtf(buf, p.itemNbt);
        buf.writeBoolean(p.mainChatEnabled);
        writeUtf(buf, p.defaultSubChannel);
    }

    private static ServerboundChannelActionPayload read(ByteBuf buf) {
        int actionIdx = buf.readInt();
        if (actionIdx < 0 || actionIdx >= Action.values().length) {
            throw new IllegalStateException("Unknown action: " + actionIdx);
        }
        Action action = Action.values()[actionIdx];
        String channelId = PayloadLimits.readUtf(buf);
        UUID owner = UUID.fromString(PayloadLimits.readUtf(buf));
        boolean isPublic = buf.readBoolean();
        String description = PayloadLimits.readUtf(buf);
        String displayName = PayloadLimits.readUtf(buf);
        List<String> admins = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
        List<String> muted = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
        List<String> invited = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
        String inviteCode = PayloadLimits.readUtf(buf);
        boolean showInExplore = buf.readBoolean();
        String replyContent = PayloadLimits.readUtf(buf);
        String replySender = PayloadLimits.readUtf(buf);
        String itemNbt = PayloadLimits.readUtf(buf);
        boolean mainChatEnabled = buf.readBoolean();
        String defaultSubChannel = PayloadLimits.readUtf(buf);
        return new ServerboundChannelActionPayload(action, channelId, owner, isPublic, description, displayName, admins, muted, invited, inviteCode, showInExplore, replyContent, replySender, itemNbt, mainChatEnabled, defaultSubChannel);
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }

    private static void writeStringList(ByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) writeUtf(buf, s);
    }

    private static List<String> readStringList(ByteBuf buf) {
        return PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            var server = player.getServer();
            if (server == null) return;
            UUID realUuid = player.getUUID();
            ModServerChannels msc = ModServerChannels.getInstance(server);
            switch (action) {
                case CREATE -> msc.createChannel(channelId, realUuid, isPublic, showInExplore, mainChatEnabled, defaultSubChannel);
                case UPDATE_CONFIG -> msc.updateChannelConfig(channelId, isPublic, description, displayName, admins, mutedPlayers, invitedPlayers, inviteCode, realUuid, showInExplore, mainChatEnabled, defaultSubChannel);
                case JOIN_MEMBER -> {
                    msc.addMemberToChannel(channelId, realUuid.toString());
                }
                case JOIN_BY_CODE -> {
                    if (inviteCode != null && !inviteCode.isEmpty()) {
                        String result = msc.joinByCode(inviteCode, realUuid);
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            Component msg = switch (result) {
                                case "already_member" -> Component.translatable(
                                        "screen.chatsphere.join_channel.result_already_member");
                                case "not_found" -> Component.translatable(
                                        "screen.chatsphere.join_channel.result_not_found", inviteCode);
                                default -> Component.translatable(
                                        "screen.chatsphere.join_channel.result_success");
                            };
                            sp.sendSystemMessage(msg, false);
                        }
                    }
                }
                case SEND_CHAT -> {
                    if (!channelId.isEmpty() && !description.isEmpty()) {
                        String senderName = player.getName().getString();
                        String convType;
                        String chatChannelId = channelId;
                        UUID targetUuid = null;
                        boolean muted = false;
                        boolean notMember = false;

                        if (chatChannelId.contains(":")) {
                            convType = "PRIVATE";
                            String senderStr = realUuid.toString();
                            String[] parts = chatChannelId.split(":");
                            String otherStr = parts[0].equals(senderStr) ? parts[1] : parts[0];
                            try { targetUuid = UUID.fromString(otherStr); } catch (Exception ignored) {}
                        } else {
                            convType = "CHANNEL";
                            String chatTarget = msc.resolveChatChannel(chatChannelId);
                            if (chatTarget == null) {
                                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
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
                            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                sp.sendSystemMessage(Component.translatable("chatsphere.mute.feedback"), false);
                            }
                            return;
                        }

                        String bannedRaw = cn.sarskin.ChatSphere.config.ModServerConfig.CONFIG.bannedWords.get();
                        if (!bannedRaw.isEmpty()) {
                            String[] patterns = bannedRaw.split("\n");
                            for (String p : patterns) {
                                p = p.trim();
                                if (p.isEmpty()) continue;
                                try {
                                    if (java.util.regex.Pattern.compile(p, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(description).find()) {
                                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                                            sp.sendSystemMessage(Component.translatable("chatsphere.banned_word.feedback"), false);
                                        }
                                        return;
                                    }
                                } catch (java.util.regex.PatternSyntaxException ignored) {}
                            }
                        }
                        msc.addChatMessage(senderName, realUuid, description, chatChannelId, convType, replyContent, replySender, itemNbt);
                        long now = System.currentTimeMillis();
                        ClientboundChatPayload relay = new ClientboundChatPayload(
                                new ClientboundChatPayload.StoredMessage(senderName, realUuid, description, now, chatChannelId, convType, replyContent, replySender, itemNbt));

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
                }
                case REMOVE_CHANNEL -> {
                    msc.removeChannel(channelId, realUuid);
                }
                case TOGGLE_MUTE -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.toggleMute(channelId, description, realUuid);
                    }
                }
                case TOGGLE_ADMIN -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.toggleAdmin(channelId, description, realUuid);
                    }
                }
                case TOGGLE_INVITE -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.toggleInvite(channelId, description, realUuid);
                    }
                }
                case KICK_MEMBER -> {
                    if (!description.isEmpty() && !channelId.isEmpty()) {
                        msc.kickMember(channelId, description, realUuid);
                    }
                }
                case LEAVE_CHANNEL -> {
                    msc.leaveChannel(channelId, realUuid);
                }
                case LIST_PUBLIC -> {
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                        var publicList = msc.getPublicChannels();
                        sp.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                                new ClientboundPublicChannelListPayload(publicList)));
                    }
                }
                case CREATE_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.createVoiceRoom(channelId, description, realUuid);
                    }
                }
                case DELETE_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.deleteVoiceRoom(channelId, description, realUuid);
                    }
                }
                case JOIN_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.joinVoiceRoom(channelId, description, realUuid);
                    }
                }
                case LEAVE_VOICE_ROOM -> {
                    if (!description.isEmpty()) {
                        msc.leaveVoiceRoom(channelId, description, realUuid);
                    }
                }
                case RENAME_SUBCHANNEL -> {
                    if (!channelId.isEmpty() && !displayName.isEmpty()) {
                        msc.renameChannel(channelId, displayName, realUuid);
                    }
                }
                case REORDER_CHANNEL -> {
                    if (!description.isEmpty()) {
                        List<String> ids = new ArrayList<>();
                        for (String s : description.split(",")) {
                            if (!s.trim().isEmpty()) ids.add(s.trim());
                        }
                        msc.reorderChannels(ids, realUuid);
                    }
                }
                case MOVE_CHANNEL -> {
                    if (!channelId.isEmpty() && !description.isEmpty()) {
                        msc.moveChannel(channelId, description, realUuid);
                    }
                }
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action { CREATE, UPDATE_CONFIG, JOIN_MEMBER, JOIN_BY_CODE, SEND_CHAT, REMOVE_CHANNEL,
        TOGGLE_MUTE, TOGGLE_ADMIN, TOGGLE_INVITE, LEAVE_CHANNEL, LIST_PUBLIC,
        CREATE_VOICE_ROOM, DELETE_VOICE_ROOM, JOIN_VOICE_ROOM, LEAVE_VOICE_ROOM,
        KICK_MEMBER, RENAME_SUBCHANNEL, REORDER_CHANNEL, MOVE_CHANNEL }
}
