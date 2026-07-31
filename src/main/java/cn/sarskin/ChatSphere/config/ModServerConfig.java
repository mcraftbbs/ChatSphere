package cn.sarskin.ChatSphere.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

public class ModServerConfig {
    public static final ModServerConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    public final ModConfigSpec.BooleanValue antiSpam;
    public final ModConfigSpec.BooleanValue enableChannels;
    public final ModConfigSpec.IntValue maxChatHistory;
    public final ModConfigSpec.IntValue maxCommandMessages;
    public final ModConfigSpec.BooleanValue showStrongHint;
    public final ModConfigSpec.BooleanValue syncDefaultChannel;
    public final ModConfigSpec.BooleanValue channelHistoryEnabled;
    public final ModConfigSpec.BooleanValue exploreEnabled;
    public final ModConfigSpec.IntValue exploreMinMembers;
    public final ModConfigSpec.IntValue backupIntervalMinutes;
    public final ModConfigSpec.IntValue backupKeepMax;
    public final ModConfigSpec.BooleanValue preventsChatReports;
    public final ModConfigSpec.ConfigValue<String> bannedWords;
    public final ModConfigSpec.BooleanValue voiceOfflineStorage;
    public final ModConfigSpec.IntValue voiceOfflineMaxAgeHours;
    public final ModConfigSpec.IntValue voiceOfflineMaxPerPlayer;

    private static final Map<String, Boolean> pendingBooleans = new HashMap<>();
    private static final Map<String, ModConfigSpec.BooleanValue> BOOL_FIELDS = new HashMap<>();

    static {
        Pair<ModServerConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ModServerConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
        BOOL_FIELDS.put("antiSpam", CONFIG.antiSpam);
        BOOL_FIELDS.put("enableChannels", CONFIG.enableChannels);
        BOOL_FIELDS.put("showStrongHint", CONFIG.showStrongHint);
        BOOL_FIELDS.put("syncDefaultChannel", CONFIG.syncDefaultChannel);
        BOOL_FIELDS.put("channelHistoryEnabled", CONFIG.channelHistoryEnabled);
        BOOL_FIELDS.put("exploreEnabled", CONFIG.exploreEnabled);
        BOOL_FIELDS.put("preventsChatReports", CONFIG.preventsChatReports);
        BOOL_FIELDS.put("voiceOfflineStorage", CONFIG.voiceOfflineStorage);
    }

    private ModServerConfig(ModConfigSpec.Builder builder) {
        builder.push("chat");
        antiSpam = builder
                .comment("Collapse consecutive duplicate messages from the same player")
                .define("antiSpam", true);
        enableChannels = builder
                .comment("Enable chat channels")
                .define("enableChannels", true);
        maxChatHistory = builder
                .comment("Maximum number of chat messages to keep per conversation")
                .defineInRange("maxChatHistory", 200, 50, 1000);
        maxCommandMessages = builder
                .comment("Maximum number of command console messages to keep (independent of chat history)")
                .defineInRange("maxCommandMessages", 500, 50, 2000);
        showStrongHint = builder
                .comment("Show strong hint above hotbar for mentions and system messages")
                .define("showStrongHint", true);
        backupIntervalMinutes = builder
                .comment("Minutes between automatic server data backups (0 to disable)")
                .defineInRange("backupIntervalMinutes", 30, 0, 1440);
        backupKeepMax = builder
                .comment("Maximum number of backup files to keep")
                .defineInRange("backupKeepMax", 20, 1, 100);
        builder.pop();
        builder.push("sync");
        syncDefaultChannel = builder
                .comment("Sync the default channel (#general) to all players on login")
                .define("syncDefaultChannel", true);
        channelHistoryEnabled = builder
                .comment("Enable broadcast of channel chat history on login (does not affect private messages)")
                .define("channelHistoryEnabled", true);
        builder.pop();
        builder.push("explore");
        exploreEnabled = builder
                .comment("Enable the Explore Public Servers feature")
                .define("exploreEnabled", true);
        exploreMinMembers = builder
                .comment("Minimum number of members required for a channel to appear in Explore")
                .defineInRange("exploreMinMembers", 0, 0, 100);
        builder.pop();
        builder.push("ncr");
        preventsChatReports = builder
                .comment("Mark this server as preventing chat reports (adds preventsChatReports to ping response)")
                .define("preventsChatReports", false);
        builder.pop();
        builder.push("bannedWords");
        bannedWords = builder
                .comment("Banned words/regex patterns (one per line). Messages matching any pattern will be blocked server-side.")
                .define("bannedWords", "");
        builder.pop();
        builder.push("voice");
        voiceOfflineStorage = builder
                .comment("Enable offline voice message storage and delivery for private messages and channel members")
                .define("voiceOfflineStorage", true);
        voiceOfflineMaxAgeHours = builder
                .comment("Maximum age in hours for undelivered voice messages before they are deleted")
                .defineInRange("voiceOfflineMaxAgeHours", 24, 1, 168);
        voiceOfflineMaxPerPlayer = builder
                .comment("Maximum number of undelivered voice messages stored per player")
                .defineInRange("voiceOfflineMaxPerPlayer", 10, 1, 50);
        builder.pop();
    }

    public static boolean safeSetBool(ModConfigSpec.BooleanValue cfg, String fieldName, boolean v) {
        try {
            cfg.set(v);
            return true;
        } catch (IllegalStateException e) {
            pendingBooleans.put(fieldName, v);
            return false;
        }
    }

    public static Map<String, Boolean> flushPendingBooleans() {
        if (pendingBooleans.isEmpty()) return Map.of();
        boolean loaded;
        try {
            BOOL_FIELDS.get("antiSpam").get();
            loaded = true;
        } catch (IllegalStateException e) {
            loaded = false;
        }
        Map<String, Boolean> toSend = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : pendingBooleans.entrySet()) {
            ModConfigSpec.BooleanValue cv = BOOL_FIELDS.get(entry.getKey());
            if (cv == null) continue;
            if (loaded) {
                try {
                    cv.set(entry.getValue());
                } catch (IllegalStateException ignored) {}
            } else {
                toSend.put(entry.getKey(), entry.getValue());
            }
        }
        if (loaded) {
            try {
                CONFIG_SPEC.save();
            } catch (Exception ignored) {}
        }
        pendingBooleans.clear();
        return toSend;
    }
}
