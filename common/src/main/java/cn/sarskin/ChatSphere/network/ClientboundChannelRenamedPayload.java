package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record ClientboundChannelRenamedPayload(String oldId, String newId)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "channel_renamed");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ClientboundChannelRenamedPayload p) {
        writeUtf(buf, p.oldId);
        writeUtf(buf, p.newId);
    }

    public static ClientboundChannelRenamedPayload read(FriendlyByteBuf buf) {
        return new ClientboundChannelRenamedPayload(readUtf(buf), readUtf(buf));
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }


    }