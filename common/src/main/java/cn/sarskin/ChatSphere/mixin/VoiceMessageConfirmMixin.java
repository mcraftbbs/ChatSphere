package cn.sarskin.ChatSphere.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

@Mixin(targets = "ru.dimaskama.voicemessages.client.screen.VoiceMessageConfirmScreen")
public class VoiceMessageConfirmMixin {

    @Inject(method = "send", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSend(CallbackInfo ci) {
        try {
            Field targetField = getClass().getDeclaredField("target");
            targetField.setAccessible(true);
            String target = (String) targetField.get(this);
            if (!"chatsphere_internal".equals(target)) return;

            Field playbackField = getClass().getDeclaredField("playback");
            playbackField.setAccessible(true);
            Object playback = playbackField.get(this);

            Method getAudio = playback.getClass().getMethod("getAudio");
            List<short[]> audio = (List<short[]>) getAudio.invoke(playback);

            Class<?> pmCls = Class.forName("ru.dimaskama.voicemessages.client.PlaybackManager");
            Object pm = pmCls.getField("MAIN").get(null);
            Method addFromChat = pmCls.getMethod("addFromChat", List.class);
            addFromChat.invoke(pm, audio);

            ci.cancel();
        } catch (Exception ignored) {}
    }
}
