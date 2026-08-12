package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ServerboundCommandMessagePayload(String content, UUID senderUuid)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "command_message");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ServerboundCommandMessagePayload p) {
        byte[] cb = p.content.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(cb.length); buf.writeBytes(cb);
        buf.writeLong(p.senderUuid.getMostSignificantBits());
        buf.writeLong(p.senderUuid.getLeastSignificantBits());
    }

    public static ServerboundCommandMessagePayload read(FriendlyByteBuf buf) {
        int cl = PayloadLimits.readCount(buf, PayloadLimits.MAX_UTF_BYTES);
        byte[] cb = new byte[cl]; buf.readBytes(cb);
        String content = new String(cb, StandardCharsets.UTF_8);
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        return new ServerboundCommandMessagePayload(content, uuid);
    }

    }