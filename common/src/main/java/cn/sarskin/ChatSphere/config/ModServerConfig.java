package cn.sarskin.ChatSphere.config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModServerConfig {
    public static final ModServerConfig CONFIG = new ModServerConfig();
    public static final ConfigStore CONFIG_SPEC = CONFIG.store;

    public final ConfigStore store;

    public final CfgValue.Bool antiSpam;
    public final CfgValue.Bool enableChannels;
    public final CfgValue.Int maxChatHistory;
    public final CfgValue.Int maxCommandMessages;
    public final CfgValue.Bool showStrongHint;
    public final CfgValue.Bool syncDefaultChannel;
    public final CfgValue.Bool channelHistoryEnabled;
    public final CfgValue.Bool exploreEnabled;
    public final CfgValue.Int exploreMinMembers;
    public final CfgValue.Int backupIntervalMinutes;
    public final CfgValue.Int backupKeepMax;
    public final CfgValue.Bool preventsChatReports;
    public final CfgValue.Str bannedWords;
    public final CfgValue.Bool voiceOfflineStorage;
    public final CfgValue.Int voiceOfflineMaxAgeHours;
    public final CfgValue.Int voiceOfflineMaxPerPlayer;
    public final CfgValue.Int voiceStorageMax;

    private final Map<String, CfgValue.Bool> boolFields = new HashMap<>();
    private static final Map<String, Boolean> PENDING_BOOLEANS = new ConcurrentHashMap<>();

    private ModServerConfig() {
        this.store = new ConfigStore("chatsphere-server.json");

        antiSpam = new CfgValue.Bool(store, "antiSpam", true);
        enableChannels = new CfgValue.Bool(store, "enableChannels", true);
        maxChatHistory = new CfgValue.Int(store, "maxChatHistory", 200);
        maxCommandMessages = new CfgValue.Int(store, "maxCommandMessages", 500);
        showStrongHint = new CfgValue.Bool(store, "showStrongHint", true);
        backupIntervalMinutes = new CfgValue.Int(store, "backupIntervalMinutes", 30);
        backupKeepMax = new CfgValue.Int(store, "backupKeepMax", 20);

        syncDefaultChannel = new CfgValue.Bool(store, "syncDefaultChannel", true);
        channelHistoryEnabled = new CfgValue.Bool(store, "channelHistoryEnabled", true);

        exploreEnabled = new CfgValue.Bool(store, "exploreEnabled", true);
        exploreMinMembers = new CfgValue.Int(store, "exploreMinMembers", 0);

        preventsChatReports = new CfgValue.Bool(store, "preventsChatReports", false);

        bannedWords = new CfgValue.Str(store, "bannedWords", "");

        voiceOfflineStorage = new CfgValue.Bool(store, "voiceOfflineStorage", true);
        voiceOfflineMaxAgeHours = new CfgValue.Int(store, "voiceOfflineMaxAgeHours", 24);
        voiceOfflineMaxPerPlayer = new CfgValue.Int(store, "voiceOfflineMaxPerPlayer", 10);
        voiceStorageMax = new CfgValue.Int(store, "voiceStorageMax", 512);

        boolFields.put("antiSpam", antiSpam);
        boolFields.put("enableChannels", enableChannels);
        boolFields.put("showStrongHint", showStrongHint);
        boolFields.put("syncDefaultChannel", syncDefaultChannel);
        boolFields.put("channelHistoryEnabled", channelHistoryEnabled);
        boolFields.put("exploreEnabled", exploreEnabled);
        boolFields.put("preventsChatReports", preventsChatReports);
        boolFields.put("voiceOfflineStorage", voiceOfflineStorage);

        store.save();
    }

    public static boolean safeSetBool(CfgValue.Bool cfg, String fieldName, boolean v) {
        try {
            cfg.set(v);
            return true;
        } catch (RuntimeException e) {
            PENDING_BOOLEANS.put(fieldName, v);
            return false;
        }
    }

    /** Records a boolean change made while offline so it can be synced on next login. */
    public static void queuePendingUpdate(String fieldName, String value) {
        if (fieldName == null || value == null) return;
        if (!CONFIG.boolFields.containsKey(fieldName)) return;
        PENDING_BOOLEANS.put(fieldName, Boolean.parseBoolean(value));
    }

    public static Map<String, Boolean> flushPendingBooleans() {
        if (PENDING_BOOLEANS.isEmpty()) return Map.of();
        Map<String, Boolean> toSend = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : PENDING_BOOLEANS.entrySet()) {
            CfgValue.Bool cv = CONFIG.boolFields.get(entry.getKey());
            if (cv == null) continue;
            cv.set(entry.getValue());
            toSend.put(entry.getKey(), entry.getValue());
        }
        try {
            CONFIG_SPEC.save();
        } catch (Exception ignored) {
        }
        PENDING_BOOLEANS.clear();
        return toSend;
    }
}
