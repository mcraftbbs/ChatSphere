package cn.sarskin.ChatSphere.mixin;

import cn.sarskin.ChatSphere.client.ModVoiceMessagesIntegration;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

@Mixin(targets = "ru.dimaskama.voicemessages.client.networking.VoiceMessagesClientNetworking")
public class VoiceMessagesClientMixin {

    @Inject(method = "lambda$onVoiceMessageEndReceived$0",
            at = @At("HEAD"), remap = false, require = 0)
    private static void onLambdaVoiceMessageEnd(Minecraft minecraft, UUID senderUuid, int duration, String target, List frames, CallbackInfo ci) {
        ModVoiceMessagesIntegration.pushVoiceMessageContext(senderUuid, target);
    }
}
