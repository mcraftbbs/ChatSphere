package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ClientboundPublicChannelListPayload(List<PublicChannelEntry> channels)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "public_channel_list");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ClientboundPublicChannelListPayload p) {
        buf.writeInt(p.channels.size());
        for (PublicChannelEntry e : p.channels) {
            writeUtf(buf, e.channelId());
            writeUtf(buf, e.displayName());
            writeUtf(buf, e.description());
            buf.writeInt(e.memberCount());
            buf.writeInt(e.onlineCount());
            writeUtf(buf, e.inviteCode());
        }
    }

    public static ClientboundPublicChannelListPayload read(FriendlyByteBuf buf) {
        int count = PayloadLimits.readCount(buf, PayloadLimits.MAX_CHANNELS);
        List<PublicChannelEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String channelId = PayloadLimits.readUtf(buf);
            String displayName = PayloadLimits.readUtf(buf);
            String description = PayloadLimits.readUtf(buf);
            int memberCount = buf.readInt();
            int onlineCount = buf.readInt();
            String inviteCode = PayloadLimits.readUtf(buf);
            list.add(new PublicChannelEntry(channelId, displayName, description, memberCount, onlineCount, inviteCode));
        }
        return new ClientboundPublicChannelListPayload(list);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }


    
    public record PublicChannelEntry(String channelId, String displayName, String description,
                                     int memberCount, int onlineCount, String inviteCode) {}
}