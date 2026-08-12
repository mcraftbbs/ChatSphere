package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

public record ServerboundPermissionCheckPayload(String scope)  {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "perm_check");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        write(buf, this);
        return buf;
    }

    
    private static void write(FriendlyByteBuf buf, ServerboundPermissionCheckPayload p) {
        byte[] bytes = p.scope.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    public static ServerboundPermissionCheckPayload read(FriendlyByteBuf buf) {
        byte[] bytes = PayloadLimits.readBytes(buf, PayloadLimits.MAX_UTF_BYTES);
        return new ServerboundPermissionCheckPayload(new String(bytes, StandardCharsets.UTF_8));
    }


    }