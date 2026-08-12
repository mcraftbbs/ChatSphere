package cn.sarskin.ChatSphere.mixin;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import cn.sarskin.ChatSphere.server.ModServerChannels.ChannelEntry;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(targets = "ru.dimaskama.voicemessages.networking.VoiceMessagesServerNetworking")
public class VoiceMessageServerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModInfo.MODID + "-VMServer");

    @Inject(method = "sendVoiceMessage(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/List;Ljava/lang/String;)V", at = @At("HEAD"), remap = false, cancellable = true, require = 0)
    private static void onSendVoiceMessage(ServerPlayer sender, List<byte[]> message, String target, CallbackInfo ci) {
        if (target == null || !target.startsWith("#")) return;

        try {
            ModServerChannels channels = ModServerChannels.getInstance(sender.server);
            ChannelEntry entry = channels.getChannel(target);
            if (entry == null) return;
            String senderUuidStr = sender.getUUID().toString();
            if (!entry.members().contains(senderUuidStr)) return;

            List<ServerPlayer> recipients = new ArrayList<>();
            for (String memberUuid : entry.members()) {
                ServerPlayer p = sender.server.getPlayerList().getPlayer(UUID.fromString(memberUuid));
                if (p != null) recipients.add(p);
            }
            if (recipients.isEmpty()) return;

            Class<?> cls = Class.forName("ru.dimaskama.voicemessages.networking.VoiceMessagesServerNetworking");
            Method sendMsg = cls.getDeclaredMethod("sendVoiceMessage", UUID.class, Iterable.class, List.class, String.class);
            sendMsg.setAccessible(true);
            sendMsg.invoke(null, sender.getUUID(), recipients, message, target);

            LOGGER.info("Routed voice message to channel {} ({} recipients)", target, recipients.size());
            ci.cancel();
        } catch (Exception e) {
            LOGGER.error("Failed to route voice message to channel {}", target, e);
        }
    }
}
