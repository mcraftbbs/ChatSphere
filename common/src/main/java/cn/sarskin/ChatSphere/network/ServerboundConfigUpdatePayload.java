package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record ServerboundConfigUpdatePayload(String key, String value) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundConfigUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "config_update"));

    public static final StreamCodec<ByteBuf, ServerboundConfigUpdatePayload> STREAM_CODEC =
            StreamCodec.of(ServerboundConfigUpdatePayload::write, ServerboundConfigUpdatePayload::read);

    private static void write(ByteBuf buf, ServerboundConfigUpdatePayload p) {
        byte[] k = p.key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] v = p.value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(k.length); buf.writeBytes(k);
        buf.writeInt(v.length); buf.writeBytes(v);
    }

    private static ServerboundConfigUpdatePayload read(ByteBuf buf) {
        int kl = PayloadLimits.readCount(buf, PayloadLimits.MAX_UTF_BYTES);
        byte[] k = new byte[kl]; buf.readBytes(k);
        int vl = PayloadLimits.readCount(buf, PayloadLimits.MAX_UTF_BYTES);
        byte[] v = new byte[vl]; buf.readBytes(v);
        return new ServerboundConfigUpdatePayload(
            new String(k, java.nio.charset.StandardCharsets.UTF_8),
            new String(v, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

}