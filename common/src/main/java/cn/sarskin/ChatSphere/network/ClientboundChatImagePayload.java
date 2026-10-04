package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.server.ModServerImages;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;

/** Upload result, one chunk of a stored image, or a failure reason. */
public record ClientboundChatImagePayload(Action action, String id, int width, int height,
                                          int partIndex, int partCount, String reason, byte[] data)
        implements CustomPacketPayload {

    public enum Action { ACCEPTED, DATA, FAILED }

    public static final CustomPacketPayload.Type<ClientboundChatImagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "chat_image_relay"));

    public static final StreamCodec<ByteBuf, ClientboundChatImagePayload> STREAM_CODEC =
            StreamCodec.of(ClientboundChatImagePayload::write, ClientboundChatImagePayload::read);

    private static void write(ByteBuf buf, ClientboundChatImagePayload p) {
        buf.writeInt(p.action.ordinal());
        writeUtf(buf, p.id);
        buf.writeInt(p.width);
        buf.writeInt(p.height);
        buf.writeInt(p.partIndex);
        buf.writeInt(p.partCount);
        writeUtf(buf, p.reason);
        buf.writeInt(p.data.length);
        buf.writeBytes(p.data);
    }

    private static ClientboundChatImagePayload read(ByteBuf buf) {
        int actionIdx = buf.readInt();
        if (actionIdx < 0 || actionIdx >= Action.values().length) throw new IllegalStateException("bad action");
        String id = PayloadLimits.readUtf(buf);
        int width = buf.readInt();
        int height = buf.readInt();
        int partIndex = buf.readInt();
        int partCount = buf.readInt();
        String reason = PayloadLimits.readUtf(buf);
        if (partIndex < 0 || partCount < 0 || partCount > ModServerImages.MAX_CHUNKS) {
            throw new IllegalStateException("bad chunk range");
        }
        byte[] data = PayloadLimits.readBytes(buf, ModServerImages.CHUNK_BYTES);
        return new ClientboundChatImagePayload(Action.values()[actionIdx], id, width, height, partIndex, partCount, reason, data);
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static ClientboundChatImagePayload accepted(String id, int width, int height) {
        return new ClientboundChatImagePayload(Action.ACCEPTED, id, width, height, 0, 0, "", new byte[0]);
    }

    public static ClientboundChatImagePayload data(String id, int partIndex, int partCount, byte[] data) {
        return new ClientboundChatImagePayload(Action.DATA, id, 0, 0, partIndex, partCount, "", data);
    }

    public static ClientboundChatImagePayload failed(String id, String reason) {
        return new ClientboundChatImagePayload(Action.FAILED, id, 0, 0, 0, 0, reason, new byte[0]);
    }

    public static void sendTo(ServerPlayer player, ClientboundChatImagePayload payload) {
        if (player == null) return;
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
