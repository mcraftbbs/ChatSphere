package cn.sarskin.ChatSphere.config;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    public final CfgValue.Bool emojiSharingEnabled;
    public final CfgValue.Bool emojiUploadRequiresOp;
    public final CfgValue.Int emojiUploadCooldownSeconds;
    public final CfgValue.Int emojiMaxTotal;

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

        emojiSharingEnabled = new CfgValue.Bool(store, "emojiSharingEnabled", true);
        emojiUploadRequiresOp = new CfgValue.Bool(store, "emojiUploadRequiresOp", true);
        emojiUploadCooldownSeconds = new CfgValue.Int(store, "emojiUploadCooldownSeconds", 5);
        // cap applies per folder
        emojiMaxTotal = new CfgValue.Int(store, "emojiMaxTotal", 100);

        boolFields.put("antiSpam", antiSpam);
        boolFields.put("enableChannels", enableChannels);
        boolFields.put("showStrongHint", showStrongHint);
        boolFields.put("syncDefaultChannel", syncDefaultChannel);
        boolFields.put("channelHistoryEnabled", channelHistoryEnabled);
        boolFields.put("exploreEnabled", exploreEnabled);
        boolFields.put("preventsChatReports", preventsChatReports);
        boolFields.put("voiceOfflineStorage", voiceOfflineStorage);
        boolFields.put("emojiSharingEnabled", emojiSharingEnabled);
        boolFields.put("emojiUploadRequiresOp", emojiUploadRequiresOp);

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

    /** Queued while offline; synced on next login. */
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

    /** All config values as key → string, for the server → client config sync. */
    public static Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Field f : ModServerConfig.class.getDeclaredFields()) {
            if (!CfgValue.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object val = f.get(CONFIG);
                String v;
                if (val instanceof CfgValue.Bool bv) {
                    v = String.valueOf(bv.get());
                } else if (val instanceof CfgValue.Int iv) {
                    v = String.valueOf(iv.get());
                } else if (val instanceof CfgValue.Str sv) {
                    v = sv.get() != null ? sv.get() : "";
                } else {
                    continue;
                }
                out.put(f.getName(), v);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Applies a server-sent value (keeps the client copy in sync). */
    public static void applyValue(String key, String value) {
        if (key == null || value == null) return;
        try {
            Field field = ModServerConfig.class.getField(key);
            Object val = field.get(CONFIG);
            if (val instanceof CfgValue.Bool bv) {
                bv.set(Boolean.parseBoolean(value));
            } else if (val instanceof CfgValue.Int iv) {
                int v = Integer.parseInt(value);
                if (v < 0 || v > 1_000_000) return;
                iv.set(v);
            } else if (val instanceof CfgValue.Str sv) {
                sv.set(value);
            }
            CONFIG_SPEC.save();
        } catch (Exception ignored) {
        }
    }
}
