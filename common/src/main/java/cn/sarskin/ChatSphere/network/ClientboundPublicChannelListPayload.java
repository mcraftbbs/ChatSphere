package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record ClientboundPublicChannelListPayload(List<PublicChannelEntry> channels) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundPublicChannelListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "public_channel_list"));

    public static final StreamCodec<ByteBuf, ClientboundPublicChannelListPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundPublicChannelListPayload::write, ClientboundPublicChannelListPayload::read);

    private static void write(ByteBuf buf, ClientboundPublicChannelListPayload p) {
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

    private static ClientboundPublicChannelListPayload read(ByteBuf buf) {
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

    private static void writeUtf(ByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }


    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record PublicChannelEntry(String channelId, String displayName, String description,
                                     int memberCount, int onlineCount, String inviteCode) {}
}