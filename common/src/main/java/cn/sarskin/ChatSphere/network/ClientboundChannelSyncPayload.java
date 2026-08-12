package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ClientboundChannelSyncPayload(List<ModServerChannels.ChannelEntry> channels, Map<String, String> knownPlayers)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "channel_sync");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ClientboundChannelSyncPayload p) {
        buf.writeInt(p.channels.size());
        for (ModServerChannels.ChannelEntry e : p.channels) {
            writeUtf(buf, e.id());
            writeUtf(buf, e.owner());
            buf.writeBoolean(e.isPublic());
            writeUtf(buf, e.description());
            writeUtf(buf, e.displayName());
            writeStringList(buf, e.admins());
            writeStringList(buf, e.mutedPlayers());
            writeStringList(buf, e.invitedPlayers());
            writeStringList(buf, e.members());
            writeUtf(buf, e.inviteCode());
            buf.writeBoolean(e.showInExplore());
            List<ModServerChannels.VoiceRoom> rooms = e.voiceRooms();
            buf.writeInt(rooms.size());
            for (ModServerChannels.VoiceRoom vr : rooms) {
                writeUtf(buf, vr.name());
                writeStringList(buf, vr.members());
            }
            writeUtf(buf, e.parentId() != null ? e.parentId() : "");
            buf.writeInt(e.sortOrder());
            buf.writeBoolean(e.mainChatEnabled());
            writeUtf(buf, e.defaultSubChannel() != null ? e.defaultSubChannel() : "");
        }
        Map<String, String> kp = p.knownPlayers != null ? p.knownPlayers : Map.of();
        buf.writeInt(kp.size());
        for (Map.Entry<String, String> entry : kp.entrySet()) {
            writeUtf(buf, entry.getKey());
            writeUtf(buf, entry.getValue());
        }
    }

    public static ClientboundChannelSyncPayload read(FriendlyByteBuf buf) {
        int count = PayloadLimits.readCount(buf, PayloadLimits.MAX_CHANNELS);
        List<ModServerChannels.ChannelEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = PayloadLimits.readUtf(buf);
            String owner = PayloadLimits.readUtf(buf);
            boolean isPublic = buf.readBoolean();
            String description = PayloadLimits.readUtf(buf);
            String displayName = PayloadLimits.readUtf(buf);
            List<String> admins = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
            List<String> muted = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
            List<String> invited = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
            List<String> members = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
            String inviteCode = PayloadLimits.readUtf(buf);
            boolean showInExplore = buf.readBoolean();
            int vrCount = PayloadLimits.readCount(buf, PayloadLimits.MAX_VOICE_ROOMS);
            List<ModServerChannels.VoiceRoom> rooms = new ArrayList<>(vrCount);
            for (int j = 0; j < vrCount; j++) {
                String vrName = PayloadLimits.readUtf(buf);
                List<String> vrMembers = PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
                rooms.add(new ModServerChannels.VoiceRoom(vrName, vrMembers));
            }
            String parentId = PayloadLimits.readUtf(buf);
            int sortOrder = buf.readInt();
            boolean mainChatEnabled = buf.readBoolean();
            String defaultSubChannel = PayloadLimits.readUtf(buf);
            list.add(new ModServerChannels.ChannelEntry(id, owner, isPublic, description, displayName, admins, muted, invited, members, inviteCode, showInExplore, rooms, parentId, sortOrder, mainChatEnabled, defaultSubChannel));
        }
        int kpSize = PayloadLimits.readCount(buf, PayloadLimits.MAX_PLAYERS);
        Map<String, String> knownPlayers = new HashMap<>(kpSize);
        for (int i = 0; i < kpSize; i++) {
            String uuid = PayloadLimits.readUtf(buf);
            String name = PayloadLimits.readUtf(buf);
            knownPlayers.put(uuid, name);
        }
        return new ClientboundChannelSyncPayload(list, knownPlayers);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) writeUtf(buf, s);
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        return PayloadLimits.readStringList(buf, PayloadLimits.MAX_STRINGS_PER_LIST);
    }


    }