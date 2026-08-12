package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

public record ClientboundPermissionResponsePayload(String scope, boolean allowed)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "perm_response");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ClientboundPermissionResponsePayload p) {
        byte[] bytes = p.scope.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
        buf.writeBoolean(p.allowed);
    }

    public static ClientboundPermissionResponsePayload read(FriendlyByteBuf buf) {
        byte[] bytes = PayloadLimits.readBytes(buf, PayloadLimits.MAX_UTF_BYTES);
        String scope = new String(bytes, StandardCharsets.UTF_8);
        boolean allowed = buf.readBoolean();
        return new ClientboundPermissionResponsePayload(scope, allowed);
    }


    }