package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ServerboundCommandMessagePayload(String content, UUID senderUuid) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundCommandMessagePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "command_message"));

    public static final StreamCodec<ByteBuf, ServerboundCommandMessagePayload> STREAM_CODEC =
            StreamCodec.of(ServerboundCommandMessagePayload::write, ServerboundCommandMessagePayload::read);

    private static void write(ByteBuf buf, ServerboundCommandMessagePayload p) {
        byte[] cb = p.content.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(cb.length); buf.writeBytes(cb);
        buf.writeLong(p.senderUuid.getMostSignificantBits());
        buf.writeLong(p.senderUuid.getLeastSignificantBits());
    }

    private static ServerboundCommandMessagePayload read(ByteBuf buf) {
        int cl = PayloadLimits.readCount(buf, PayloadLimits.MAX_UTF_BYTES);
        byte[] cb = new byte[cl]; buf.readBytes(cb);
        String content = new String(cb, StandardCharsets.UTF_8);
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        return new ServerboundCommandMessagePayload(content, uuid);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player == null) return;
            var server = player.getServer();
            if (server == null) return;
            ModServerChannels msc = ModServerChannels.getInstance(server);
            String name = player.getName().getString();
            msc.addCommandMessage(name, player.getUUID(), content);
        });
    }
}
