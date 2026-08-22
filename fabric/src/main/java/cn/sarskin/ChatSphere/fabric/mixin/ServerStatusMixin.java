package cn.sarskin.ChatSphere.fabric.mixin;

import cn.sarskin.ChatSphere.config.ModServerConfig;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.status.ClientboundStatusResponsePacket;
import net.minecraft.network.protocol.status.ServerStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric equivalent of the NeoForge ServerStatusSerializerMixin: re-encode status JSON with the preventsChatReports marker when enabled. */
@Mixin(ClientboundStatusResponsePacket.class)
public abstract class ServerStatusMixin {

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void chatsphere$writeWithNcrMarker(FriendlyByteBuf buf, CallbackInfo ci) {
        if (!ModServerConfig.CONFIG.preventsChatReports.get()) return;
        ClientboundStatusResponsePacket self = (ClientboundStatusResponsePacket) (Object) this;
        try {
            JsonElement json = ServerStatus.CODEC
                    .encodeStart(JsonOps.INSTANCE, self.status())
                    .result()
                    .orElseThrow(() -> new IllegalStateException("Failed to encode server status"));
            json.getAsJsonObject().addProperty("preventsChatReports", true);
            buf.writeUtf(json.toString());
            ci.cancel();
        } catch (RuntimeException e) {
            // Fall back to the vanilla encoding rather than breaking the status response.
            org.slf4j.LoggerFactory.getLogger("ChatSphere/NCR").warn("Failed to inject preventsChatReports marker", e);
        }
    }
}
