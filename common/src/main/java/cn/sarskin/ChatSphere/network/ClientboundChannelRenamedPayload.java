package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundChannelRenamedPayload(String oldId, String newId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundChannelRenamedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "channel_renamed"));

    public static final StreamCodec<ByteBuf, ClientboundChannelRenamedPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChannelRenamedPayload::write, ClientboundChannelRenamedPayload::read);

    private static void write(ByteBuf buf, ClientboundChannelRenamedPayload p) {
        writeUtf(buf, p.oldId);
        writeUtf(buf, p.newId);
    }

    private static ClientboundChannelRenamedPayload read(ByteBuf buf) {
        return new ClientboundChannelRenamedPayload(readUtf(buf), readUtf(buf));
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
}