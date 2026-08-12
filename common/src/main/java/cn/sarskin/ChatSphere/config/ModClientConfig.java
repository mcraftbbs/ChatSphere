package cn.sarskin.ChatSphere.config;

import java.util.ArrayList;
import java.util.List;

public class ModClientConfig {
    public static final ModClientConfig CONFIG = new ModClientConfig();
    public static final ConfigStore CONFIG_SPEC = CONFIG.store;

    public final ConfigStore store;

    public final CfgValue.Bool showTimestamp;
    public final CfgValue.Bool showSenderName;
    public final CfgValue.Bool showAvatar;
    public final CfgValue.Bool notificationSound;
    public final CfgValue.Bool notificationFlash;
    public final CfgValue.Bool notificationPopup;
    public final CfgValue.Bool notificationBadge;

    public final CfgValue.Bool preserveInput;
    public final CfgValue.Bool themeDark;
    public final CfgValue.Int uiCornerStyle;
    public final CfgValue.Bool backgroundBlur;
    public final CfgValue.Str bubbleColorOwn;
    public final CfgValue.Str bubbleColorOther;
    public final CfgValue.Int timeSeparatorMinutes;
    public final CfgValue.Bool soundMention;
    public final CfgValue.Bool soundWhisper;
    public final CfgValue.Bool soundSystem;
    public final CfgValue.Bool soundPublic;
    public final CfgValue.StrList quickPhrases;
    public final CfgValue.Int scrollHistoryLimit;
    public final CfgValue.Int commandHistoryLimit;
    public final CfgValue.Bool renderEmojiShortcodes;
    public final CfgValue.Bool renderRichText;
    public final CfgValue.Bool popupBorder;
    public final CfgValue.Bool ncrCompat;
    public final CfgValue.Str customSkinApiUrl;
    public final CfgValue.Bool avatarCacheEnabled;
    public final CfgValue.Bool allowVanillaConnection;
    public final CfgValue.Bool voiceCacheEnabled;
    public final CfgValue.Int voiceCacheMaxAgeHours;
    public final CfgValue.Int voiceCacheMaxMB;
    public final CfgValue.Bool customThemeActive;
    public final CfgValue.Str customThemeFile;
    public final CfgValue.StrList urlLinkFilter;

    private ModClientConfig() {
        this.store = new ConfigStore("chatsphere-client.json");

        showTimestamp = new CfgValue.Bool(store, "showTimestamp", true);
        showSenderName = new CfgValue.Bool(store, "showSenderName", true);
        showAvatar = new CfgValue.Bool(store, "showAvatar", true);
        themeDark = new CfgValue.Bool(store, "themeDark", true);
        uiCornerStyle = new CfgValue.Int(store, "uiCornerStyle", 2);
        popupBorder = new CfgValue.Bool(store, "popupBorder", true);
        backgroundBlur = new CfgValue.Bool(store, "backgroundBlur", true);
        bubbleColorOwn = new CfgValue.Str(store, "bubbleColorOwn", "#222222");
        bubbleColorOther = new CfgValue.Str(store, "bubbleColorOther", "#FFFFFF");

        preserveInput = new CfgValue.Bool(store, "preserveInput", true);
        timeSeparatorMinutes = new CfgValue.Int(store, "timeSeparatorMinutes", 5);
        quickPhrases = new CfgValue.StrList(store, "quickPhrases", new ArrayList<>());
        scrollHistoryLimit = new CfgValue.Int(store, "scrollHistoryLimit", 200);
        renderEmojiShortcodes = new CfgValue.Bool(store, "renderEmojiShortcodes", true);
        renderRichText = new CfgValue.Bool(store, "renderRichText", true);
        commandHistoryLimit = new CfgValue.Int(store, "commandHistoryLimit", 50);

        notificationSound = new CfgValue.Bool(store, "notificationSound", true);
        notificationFlash = new CfgValue.Bool(store, "notificationFlash", true);
        notificationPopup = new CfgValue.Bool(store, "notificationPopup", true);
        notificationBadge = new CfgValue.Bool(store, "notificationBadge", true);
        soundMention = new CfgValue.Bool(store, "soundMention", true);
        soundWhisper = new CfgValue.Bool(store, "soundWhisper", true);
        soundSystem = new CfgValue.Bool(store, "soundSystem", false);
        soundPublic = new CfgValue.Bool(store, "soundPublic", false);

        ncrCompat = new CfgValue.Bool(store, "ncrCompat", true);

        customSkinApiUrl = new CfgValue.Str(store, "customSkinApiUrl", "");
        avatarCacheEnabled = new CfgValue.Bool(store, "avatarCacheEnabled", true);

        allowVanillaConnection = new CfgValue.Bool(store, "allowVanillaConnection", false);

        voiceCacheEnabled = new CfgValue.Bool(store, "voiceCacheEnabled", true);
        voiceCacheMaxAgeHours = new CfgValue.Int(store, "voiceCacheMaxAgeHours", 24);
        voiceCacheMaxMB = new CfgValue.Int(store, "voiceCacheMaxMB", 512);

        customThemeActive = new CfgValue.Bool(store, "customThemeActive", false);
        customThemeFile = new CfgValue.Str(store, "customThemeFile", "");
        urlLinkFilter = new CfgValue.StrList(store, "urlLinkFilter", new ArrayList<>());

        store.save();
    }

    public static int parseHexColor(String hex, int defaultColor) {
        try {
            String h = hex.replace("#", "").trim();
            if (h.length() != 6) return defaultColor;
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }
}
