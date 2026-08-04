package cn.sarskin.ChatSphere.client;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.sarskin.ChatSphere.client.voice.VoiceRoom;

public class ChatDataStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatDataStore");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_FILE_NAME = "data.json";
    private static final String OLD_FILE_NAME = "chatsphere_data.json";
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChatSphere-IO");
        t.setDaemon(true);
        return t;
    });
    private static Path dataDir;

    private ChatDataStore() {}

    public static void setDataDir(Path dir) {
        dataDir = dir;
    }

    public static Path getDataDir() {
        return dataDir != null ? dataDir : cn.sarskin.ChatSphere.storage.ModStoragePaths.getClientBaseDir();
    }

    public static Path getDataPath() {
        return getDataDir().resolve(DATA_FILE_NAME);
    }

    static Path getOldDataPath() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(OLD_FILE_NAME);
    }

    private static void migrateIfNeeded() {
        Path oldPath = getOldDataPath();
        if (!Files.exists(oldPath)) return;
        Path newPath = getDataPath();
        try {
            if (Files.exists(newPath)) {
                Files.move(oldPath, oldPath.resolveSibling(oldPath.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Old data file migrated (new data already exists): {}", oldPath);
                return;
            }
            JsonObject obj;
            try (BufferedReader reader = Files.newBufferedReader(oldPath, StandardCharsets.UTF_8)) {
                obj = GSON.fromJson(reader, JsonObject.class);
            }
            if (obj != null) {
                Files.createDirectories(newPath.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(newPath, StandardCharsets.UTF_8)) {
                    GSON.toJson(obj, writer);
                }
            }
            Files.move(oldPath, oldPath.resolveSibling(oldPath.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Migrated data from {} to {}", oldPath, newPath);
        } catch (Exception e) {
            LOGGER.error("Failed to migrate old data file", e);
        }
    }

    public static SavedData load() {
        migrateIfNeeded();
        Path path = getDataPath();
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        if (!Files.exists(path) && Files.exists(tmp)) {
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Recovered chat data from interrupted save");
            } catch (IOException e) {
                LOGGER.error("Failed to recover temp data file", e);
            }
        }
        if (!Files.exists(path)) return new SavedData();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return new SavedData();
            return fromJson(obj);
        } catch (Exception e) {
            LOGGER.error("Failed to load chat data", e);
            return new SavedData();
        }
    }

    public static CompletableFuture<SavedData> loadAsync() {
        return CompletableFuture.supplyAsync(ChatDataStore::load, IO_EXECUTOR);
    }

    public static void save(SavedData data) {
        Path path = getDataPath();
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(toJson(data), writer);
            }
            moveAtomically(tmp, path);
        } catch (Exception e) {
            LOGGER.error("Failed to save chat data", e);
        }
    }

    private static void moveAtomically(Path tmp, Path target) {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                LOGGER.error("Failed to move temp data file into place", e2);
            }
        }
    }

    public static CompletableFuture<Void> saveAsync(SavedData data) {
        return CompletableFuture.runAsync(() -> save(data), IO_EXECUTOR);
    }

    private static JsonObject toJson(SavedData data) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);

        JsonArray messagesArr = new JsonArray();
        for (SavedMessage sm : data.messages) {
            JsonObject m = new JsonObject();
            m.addProperty("senderName", sm.senderName);
            m.addProperty("senderUuid", sm.senderUuid.toString());
            m.addProperty("content", sm.content);
            m.addProperty("timestamp", sm.timestamp);
            m.addProperty("conversationId", sm.conversationId);
            m.addProperty("conversationType", sm.conversationType);
            m.addProperty("isOwn", sm.isOwn);
            if (sm.duplicateCount > 1) m.addProperty("duplicateCount", sm.duplicateCount);
            if (sm.replyContent != null) m.addProperty("replyContent", sm.replyContent);
            if (sm.replySender != null) m.addProperty("replySender", sm.replySender);
            if (sm.itemNbt != null && !sm.itemNbt.isEmpty()) m.addProperty("itemNbt", sm.itemNbt);
            messagesArr.add(m);
        }
        root.add("messages", messagesArr);

        JsonArray cmdMessagesArr = new JsonArray();
        for (SavedMessage sm : data.commandMessages) {
            JsonObject m = new JsonObject();
            m.addProperty("senderName", sm.senderName);
            m.addProperty("senderUuid", sm.senderUuid.toString());
            m.addProperty("content", sm.content);
            m.addProperty("timestamp", sm.timestamp);
            m.addProperty("conversationId", sm.conversationId);
            m.addProperty("conversationType", sm.conversationType);
            m.addProperty("isOwn", sm.isOwn);
            if (sm.duplicateCount > 1) m.addProperty("duplicateCount", sm.duplicateCount);
            if (sm.replyContent != null) m.addProperty("replyContent", sm.replyContent);
            if (sm.replySender != null) m.addProperty("replySender", sm.replySender);
            if (sm.itemNbt != null && !sm.itemNbt.isEmpty()) m.addProperty("itemNbt", sm.itemNbt);
            cmdMessagesArr.add(m);
        }
        root.add("commandMessages", cmdMessagesArr);

        JsonArray channelsArr = new JsonArray();
        for (String ch : data.channels) {
            channelsArr.add(ch);
        }
        root.add("channels", channelsArr);

        JsonObject privObj = new JsonObject();
        for (Map.Entry<String, String> e : data.privateDisplayNames.entrySet()) {
            privObj.addProperty(e.getKey(), e.getValue());
        }
        root.add("privateConversations", privObj);

        JsonObject configsObj = new JsonObject();
        for (Map.Entry<String, ChannelConfig> e : data.channelConfigs.entrySet()) {
            ChannelConfig cfg = e.getValue();
            JsonObject c = new JsonObject();
            c.addProperty("isPublic", cfg.isPublic);
            if (cfg.owner != null) c.addProperty("owner", cfg.owner);
            c.addProperty("description", cfg.description);
            c.addProperty("displayName", cfg.displayName);
            c.addProperty("inviteCode", cfg.inviteCode);
            JsonArray adminsArr = new JsonArray();
            for (String a : cfg.admins) adminsArr.add(a);
            c.add("admins", adminsArr);
            JsonArray membersArr = new JsonArray();
            for (String m : cfg.members) membersArr.add(m);
            c.add("members", membersArr);
            JsonArray mutedArr = new JsonArray();
            for (String m : cfg.mutedPlayers) mutedArr.add(m);
            c.add("mutedPlayers", mutedArr);
            JsonArray invitesArr = new JsonArray();
            for (String i : cfg.invitedPlayers) invitesArr.add(i);
            c.add("invitedPlayers", invitesArr);
            JsonObject namesObj = new JsonObject();
            for (Map.Entry<String, String> ne : cfg.playerNames.entrySet()) {
                namesObj.addProperty(ne.getKey(), ne.getValue());
            }
            c.add("playerNames", namesObj);
            JsonArray vrArr = new JsonArray();
            for (VoiceRoom vr : cfg.voiceRooms) {
                JsonObject vrObj = new JsonObject();
                vrObj.addProperty("name", vr.name);
                JsonArray mArr = new JsonArray();
                for (String m : vr.members) mArr.add(m);
                vrObj.add("members", mArr);
                vrArr.add(vrObj);
            }
            c.add("voiceRooms", vrArr);
            configsObj.add(e.getKey(), c);
        }
        root.add("channelConfigs", configsObj);

        JsonObject cmdHistObj = new JsonObject();
        for (Map.Entry<String, List<String>> e : data.commandHistory.entrySet()) {
            JsonArray arr = new JsonArray();
            for (String cmd : e.getValue()) arr.add(cmd);
            cmdHistObj.add(e.getKey(), arr);
        }
        root.add("commandHistory", cmdHistObj);

        if (data.savedInput != null && !data.savedInput.isEmpty())
            root.addProperty("savedInput", data.savedInput);

        JsonArray blockedArr = new JsonArray();
        for (String uuid : data.blockedPlayers) blockedArr.add(uuid);
        root.add("blockedPlayers", blockedArr);

        return root;
    }

    private static SavedData fromJson(JsonObject obj) {
        SavedData data = new SavedData();

        if (obj.has("messages")) {
            JsonArray arr = obj.getAsJsonArray("messages");
            for (JsonElement el : arr) {
                JsonObject m = el.getAsJsonObject();
                int dup = m.has("duplicateCount") ? m.get("duplicateCount").getAsInt() : 1;
                String replyContent = m.has("replyContent") ? m.get("replyContent").getAsString() : null;
                String replySender = m.has("replySender") ? m.get("replySender").getAsString() : null;
                String itemNbt = m.has("itemNbt") ? m.get("itemNbt").getAsString() : null;
                SavedMessage sm = new SavedMessage(
                        m.get("senderName").getAsString(),
                        UUID.fromString(m.get("senderUuid").getAsString()),
                        m.get("content").getAsString(),
                        m.get("timestamp").getAsLong(),
                        m.get("conversationId").getAsString(),
                        m.get("conversationType").getAsString(),
                        m.get("isOwn").getAsBoolean(),
                        dup, replyContent, replySender, itemNbt
                );
                data.messages.add(sm);
            }
        }

        if (obj.has("commandMessages")) {
            JsonArray arr = obj.getAsJsonArray("commandMessages");
            for (JsonElement el : arr) {
                JsonObject m = el.getAsJsonObject();
                int dup = m.has("duplicateCount") ? m.get("duplicateCount").getAsInt() : 1;
                String replyContent = m.has("replyContent") ? m.get("replyContent").getAsString() : null;
                String replySender = m.has("replySender") ? m.get("replySender").getAsString() : null;
                String itemNbt = m.has("itemNbt") ? m.get("itemNbt").getAsString() : null;
                data.commandMessages.add(new SavedMessage(
                        m.get("senderName").getAsString(),
                        UUID.fromString(m.get("senderUuid").getAsString()),
                        m.get("content").getAsString(),
                        m.get("timestamp").getAsLong(),
                        m.get("conversationId").getAsString(),
                        m.get("conversationType").getAsString(),
                        m.get("isOwn").getAsBoolean(),
                        dup, replyContent, replySender, itemNbt
                ));
            }
        }

        if (obj.has("channels")) {
            JsonArray arr = obj.getAsJsonArray("channels");
            for (JsonElement el : arr) {
                data.channels.add(el.getAsString());
            }
        }

        if (obj.has("privateConversations")) {
            JsonObject pObj = obj.getAsJsonObject("privateConversations");
            for (Map.Entry<String, JsonElement> e : pObj.entrySet()) {
                data.privateDisplayNames.put(e.getKey(), e.getValue().getAsString());
            }
        }

        if (obj.has("channelConfigs")) {
            JsonObject configsObj = obj.getAsJsonObject("channelConfigs");
            for (Map.Entry<String, JsonElement> e : configsObj.entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                ChannelConfig cfg = new ChannelConfig();
                cfg.isPublic = c.get("isPublic").getAsBoolean();
                if (c.has("owner")) cfg.owner = c.get("owner").getAsString();
                if (c.has("description")) cfg.description = c.get("description").getAsString();
                if (c.has("displayName")) cfg.displayName = c.get("displayName").getAsString();
                cfg.inviteCode = c.has("inviteCode") ? c.get("inviteCode").getAsString() : "";
                if (c.has("admins")) {
                    JsonArray a = c.getAsJsonArray("admins");
                    for (JsonElement el : a) cfg.admins.add(el.getAsString());
                }
                if (c.has("members")) {
                    JsonArray m = c.getAsJsonArray("members");
                    for (JsonElement el : m) cfg.members.add(el.getAsString());
                }
                if (c.has("mutedPlayers")) {
                    JsonArray m = c.getAsJsonArray("mutedPlayers");
                    for (JsonElement el : m) cfg.mutedPlayers.add(el.getAsString());
                }
                if (c.has("invitedPlayers")) {
                    JsonArray i = c.getAsJsonArray("invitedPlayers");
                    for (JsonElement el : i) cfg.invitedPlayers.add(el.getAsString());
                }
                if (c.has("playerNames")) {
                    JsonObject namesObj = c.getAsJsonObject("playerNames");
                    for (Map.Entry<String, JsonElement> ne : namesObj.entrySet()) {
                        cfg.playerNames.put(ne.getKey(), ne.getValue().getAsString());
                    }
                }
                if (c.has("voiceRooms")) {
                    JsonArray vrArr = c.getAsJsonArray("voiceRooms");
                    for (JsonElement vrEl : vrArr) {
                        JsonObject vrObj = vrEl.getAsJsonObject();
                        VoiceRoom vr = new VoiceRoom();
                        vr.name = vrObj.get("name").getAsString();
                        if (vrObj.has("members")) {
                            JsonArray mArr = vrObj.getAsJsonArray("members");
                            for (JsonElement mEl : mArr) vr.members.add(mEl.getAsString());
                        }
                        cfg.voiceRooms.add(vr);
                    }
                }
                data.channelConfigs.put(e.getKey(), cfg);
            }
        }

        if (obj.has("commandHistory")) {
            JsonObject cmdHistObj = obj.getAsJsonObject("commandHistory");
            for (Map.Entry<String, JsonElement> e : cmdHistObj.entrySet()) {
                JsonArray arr = e.getValue().getAsJsonArray();
                List<String> cmds = new ArrayList<>();
                for (JsonElement el : arr) cmds.add(el.getAsString());
                data.commandHistory.put(e.getKey(), cmds);
            }
        }

        if (obj.has("savedInput"))
            data.savedInput = obj.get("savedInput").getAsString();

        if (obj.has("blockedPlayers")) {
            JsonArray arr = obj.getAsJsonArray("blockedPlayers");
            for (JsonElement el : arr) data.blockedPlayers.add(el.getAsString());
        }

        return data;
    }

    public static class SavedData {
        public final List<SavedMessage> messages = new ArrayList<>();
        public final List<SavedMessage> commandMessages = new ArrayList<>();
        public final List<String> channels = new ArrayList<>();
        public final Map<String, String> privateDisplayNames = new LinkedHashMap<>();
        public final Map<String, ChannelConfig> channelConfigs = new LinkedHashMap<>();
        public final Map<String, List<String>> commandHistory = new LinkedHashMap<>();
        public String savedInput;
        public final List<String> blockedPlayers = new ArrayList<>();
    }

    public record SavedMessage(
            String senderName, UUID senderUuid, String content,
            long timestamp, String conversationId, String conversationType, boolean isOwn,
            int duplicateCount, String replyContent, String replySender,
            String itemNbt
    ) {
        public SavedMessage(String senderName, UUID senderUuid, String content,
                            long timestamp, String conversationId, String conversationType, boolean isOwn) {
            this(senderName, senderUuid, content, timestamp, conversationId, conversationType, isOwn, 1, null, null, null);
        }
    }

    public static class ChannelConfig {
        public boolean isPublic = true;
        public String owner = "";
        public String description = "";
        public String displayName = "";
        public String inviteCode = "";
        public final List<String> admins = new ArrayList<>();
        public final List<String> members = new ArrayList<>();
        public final List<String> mutedPlayers = new ArrayList<>();
        public final List<String> invitedPlayers = new ArrayList<>();
        public final Map<String, String> playerNames = new HashMap<>();
        public boolean showInExplore = true;
        public final List<cn.sarskin.ChatSphere.client.voice.VoiceRoom> voiceRooms = new ArrayList<>();
    }
}
