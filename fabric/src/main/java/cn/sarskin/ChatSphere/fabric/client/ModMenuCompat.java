package cn.sarskin.ChatSphere.fabric.client;

import cn.sarskin.ChatSphere.client.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** ModMenu config screen entrypoint (optional; no-op if ModMenu is absent). */
public class ModMenuCompat implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
