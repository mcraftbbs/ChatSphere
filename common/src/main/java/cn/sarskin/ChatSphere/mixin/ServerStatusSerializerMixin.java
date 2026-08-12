package cn.sarskin.ChatSphere.mixin;

import cn.sarskin.ChatSphere.config.ModServerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge-only: rewrites the cached server status JSON with the
 * preventsChatReports marker (requires the NeoForge cachedServerStatus patch).
 */
@Mixin(MinecraftServer.class)
public class ServerStatusSerializerMixin {

    private static final Gson CS_GSON = new Gson();

    @Shadow
    private String cachedServerStatus;

    @Inject(method = "resetStatusCache", at = @At("TAIL"))
    private void onResetStatusCache(ServerStatus status, CallbackInfo ci) {
        if (!ModServerConfig.CONFIG.preventsChatReports.get()) return;
        if (cachedServerStatus == null) return;
        try {
            JsonObject json = CS_GSON.fromJson(cachedServerStatus, JsonObject.class);
            json.addProperty("preventsChatReports", true);
            cachedServerStatus = CS_GSON.toJson(json);
        } catch (Exception ignored) {
        }
    }
}
