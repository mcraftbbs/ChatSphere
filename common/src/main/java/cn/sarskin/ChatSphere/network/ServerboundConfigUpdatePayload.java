package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;


public record ServerboundConfigUpdatePayload(String key, String value)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "config_update");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ServerboundConfigUpdatePayload p) {
        byte[] k = p.key.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] v = p.value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(k.length); buf.writeBytes(k);
        buf.writeInt(v.length); buf.writeBytes(v);
    }

    public static ServerboundConfigUpdatePayload read(FriendlyByteBuf buf) {
        int kl = PayloadLimits.readCount(buf, PayloadLimits.MAX_UTF_BYTES);
        byte[] k = new byte[kl]; buf.readBytes(k);
        int vl = PayloadLimits.readCount(buf, PayloadLimits.MAX_UTF_BYTES);
        byte[] v = new byte[vl]; buf.readBytes(v);
        return new ServerboundConfigUpdatePayload(
            new String(k, java.nio.charset.StandardCharsets.UTF_8),
            new String(v, java.nio.charset.StandardCharsets.UTF_8));
    }

    }