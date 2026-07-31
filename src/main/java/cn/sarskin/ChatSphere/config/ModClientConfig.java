package cn.sarskin.ChatSphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class ModClientConfig {
    public static final ModClientConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    public final ModConfigSpec.BooleanValue showTimestamp;
    public final ModConfigSpec.BooleanValue showSenderName;
    public final ModConfigSpec.BooleanValue showAvatar;
    public final ModConfigSpec.BooleanValue notificationSound;
    public final ModConfigSpec.BooleanValue notificationFlash;
    public final ModConfigSpec.BooleanValue notificationPopup;
    public final ModConfigSpec.BooleanValue notificationBadge;

    public final ModConfigSpec.BooleanValue preserveInput;
    public final ModConfigSpec.BooleanValue themeDark;
    public final ModConfigSpec.IntValue uiCornerStyle;
    public final ModConfigSpec.BooleanValue backgroundBlur;
    public final ModConfigSpec.ConfigValue<String> bubbleColorOwn;
    public final ModConfigSpec.ConfigValue<String> bubbleColorOther;
    public final ModConfigSpec.IntValue bubbleCornerRadius;
    public final ModConfigSpec.IntValue timeSeparatorMinutes;
    public final ModConfigSpec.BooleanValue soundMention;
    public final ModConfigSpec.BooleanValue soundWhisper;
    public final ModConfigSpec.BooleanValue soundSystem;
    public final ModConfigSpec.BooleanValue soundPublic;
    public final ModConfigSpec.ConfigValue<List<? extends String>> quickPhrases;
    public final ModConfigSpec.IntValue scrollHistoryLimit;
    public final ModConfigSpec.IntValue commandHistoryLimit;
    public final ModConfigSpec.BooleanValue renderEmojiShortcodes;
    public final ModConfigSpec.BooleanValue ncrCompat;
    public final ModConfigSpec.ConfigValue<String> customSkinApiUrl;
    public final ModConfigSpec.BooleanValue avatarCacheEnabled;
    public final ModConfigSpec.BooleanValue allowVanillaConnection;
    public final ModConfigSpec.BooleanValue voiceCacheEnabled;
    public final ModConfigSpec.IntValue voiceCacheMaxAgeHours;

    static {
        Pair<ModClientConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ModClientConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    private ModClientConfig(ModConfigSpec.Builder builder) {
        builder.push("ui");
        showTimestamp = builder
                .comment("Show timestamp in chat bubbles")
                .define("showTimestamp", true);
        showSenderName = builder
                .comment("Show sender name in chat bubbles")
                .define("showSenderName", true);
        showAvatar = builder
                .comment("Show avatar in chat bubbles")
                .define("showAvatar", true);
        themeDark = builder
                .comment("Use dark theme")
                .define("themeDark", true);
        uiCornerStyle = builder
                .comment("Corner style for UI panels: 0=square, 1=pixel, 2=rounded")
                .defineInRange("uiCornerStyle", 2, 0, 2);
        backgroundBlur = builder
                .comment("Show blurred game view behind popup/config screens")
                .define("backgroundBlur", true);
        bubbleColorOwn = builder
                .comment("Own message bubble color (hex #RRGGBB)")
                .define("bubbleColorOwn", "#222222");
        bubbleColorOther = builder
                .comment("Other player message bubble color (hex #RRGGBB)")
                .define("bubbleColorOther", "#FFFFFF");
        bubbleCornerRadius = builder
                .comment("Bubble corner radius (0-8)")
                .defineInRange("bubbleCornerRadius", 4, 0, 8);
        builder.pop();

        builder.push("behavior");
        preserveInput = builder
                .comment("Preserve typed input when closing chat")
                .define("preserveInput", true);
        timeSeparatorMinutes = builder
                .comment("Minutes between time separators (0 = off)")
                .defineInRange("timeSeparatorMinutes", 5, 0, 60);
        quickPhrases = builder
                .comment("Quick chat phrases")
                .defineListAllowEmpty("quickPhrases", ArrayList::new, o -> o instanceof String);
        scrollHistoryLimit = builder
                .comment("Maximum number of messages to scroll back in chat history")
                .defineInRange("scrollHistoryLimit", 200, 50, 500);
        renderEmojiShortcodes = builder
                .comment("Render :shortcode: emoji patterns as actual emoji in chat messages")
                .define("renderEmojiShortcodes", true);
        commandHistoryLimit = builder
                .comment("Maximum number of recent commands to keep for up/down arrow recall")
                .defineInRange("commandHistoryLimit", 50, 10, 500);
        builder.pop();

        builder.push("notifications");
        notificationSound = builder
                .comment("Play sound on new message")
                .define("notificationSound", true);
        notificationFlash = builder
                .comment("Flash icon on new message")
                .define("notificationFlash", true);
        notificationPopup = builder
                .comment("Show popup on new message")
                .define("notificationPopup", true);
        notificationBadge = builder
                .comment("Show unread count badge on chat icon")
                .define("notificationBadge", true);
        soundMention = builder
                .comment("Sound on @mention")
                .define("soundMention", true);
        soundWhisper = builder
                .comment("Sound on private message")
                .define("soundWhisper", true);
        soundSystem = builder
                .comment("Sound on system message")
                .define("soundSystem", false);
        soundPublic = builder
                .comment("Sound on public chat message")
                .define("soundPublic", false);
        builder.pop();

        builder.push("ncr");
        ncrCompat = builder
                .comment("Enable No Chat Reports compatibility features")
                .define("ncrCompat", true);
        builder.pop();

        builder.push("skin");
        customSkinApiUrl = builder
                .comment("Custom Yggdrasil API base URL for player skin fetching (e.g. https://example.com). Leave empty to disable.")
                .define("customSkinApiUrl", "");
        avatarCacheEnabled = builder
                .comment("Cache player skin textures locally for offline/fallback display")
                .define("avatarCacheEnabled", true);
        builder.pop();

        builder.push("network");
        allowVanillaConnection = builder
                .comment("Allow connecting to servers not running NeoForge (marks all network payloads as optional)")
                .define("allowVanillaConnection", false);
        builder.pop();
        builder.push("voice_cache");
        voiceCacheEnabled = builder
                .comment("Enable local voice message caching for replay after restart")
                .define("voiceCacheEnabled", true);
        voiceCacheMaxAgeHours = builder
                .comment("Maximum age in hours for cached voice messages before cleanup")
                .defineInRange("voiceCacheMaxAgeHours", 24, 1, 168);
        builder.pop();
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
