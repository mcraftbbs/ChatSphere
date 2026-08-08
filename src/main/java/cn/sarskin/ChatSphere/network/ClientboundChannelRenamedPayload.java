package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ClientboundChannelRenamedPayload(String oldId, String newId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundChannelRenamedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "channel_renamed"));

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

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            history.applyChannelRename(oldId, newId);
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
