package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


import java.nio.charset.StandardCharsets;
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
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "channel_action"));

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
        UUID owner;
        try {
            owner = UUID.fromString(PayloadLimits.readUtf(buf));
        } catch (IllegalArgumentException e) {
            owner = new UUID(0L, 0L);
        }
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


    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action { CREATE, UPDATE_CONFIG, JOIN_MEMBER, JOIN_BY_CODE, SEND_CHAT, REMOVE_CHANNEL,
        TOGGLE_MUTE, TOGGLE_ADMIN, TOGGLE_INVITE, LEAVE_CHANNEL, LIST_PUBLIC,
        CREATE_VOICE_ROOM, DELETE_VOICE_ROOM, JOIN_VOICE_ROOM, LEAVE_VOICE_ROOM,
        KICK_MEMBER, RENAME_SUBCHANNEL, REORDER_CHANNEL, MOVE_CHANNEL }
}