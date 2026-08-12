package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public record ClientboundBridgeInfoPayload(
        int protocolVersion, String bridgeVersion, int capabilities, Set<String> onlinePlayers
)  {
    public static final int CAP_BANNED_WORDS = 1 << 0;
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "bridge_info");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ClientboundBridgeInfoPayload p) {
        buf.writeInt(p.protocolVersion);
        writeUtf(buf, p.bridgeVersion);
        buf.writeInt(p.capabilities);
        buf.writeInt(p.onlinePlayers.size());
        for (String uuid : p.onlinePlayers) {
            writeUtf(buf, uuid);
        }
    }

    public static ClientboundBridgeInfoPayload read(FriendlyByteBuf buf) {
        int protocolVersion = buf.readInt();
        String bridgeVersion = PayloadLimits.readUtf(buf);
        int capabilities = buf.readInt();
        int onlineCount = PayloadLimits.readCount(buf, PayloadLimits.MAX_PLAYERS);
        Set<String> onlinePlayers = new HashSet<>(onlineCount);
        for (int i = 0; i < onlineCount; i++) {
            onlinePlayers.add(PayloadLimits.readUtf(buf));
        }
        return new ClientboundBridgeInfoPayload(protocolVersion, bridgeVersion, capabilities, onlinePlayers);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(FriendlyByteBuf buf) {
        return PayloadLimits.readUtf(buf);
    }


    }