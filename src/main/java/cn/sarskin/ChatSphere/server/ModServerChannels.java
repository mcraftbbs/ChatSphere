package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.ModMain;
import static cn.sarskin.ChatSphere.ModMain.DEFAULT_CHANNEL_ID;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.StoredMessage;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.storage.ModStoragePaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ModServerChannels {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModServerChannels");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, ModServerChannels> INSTANCES = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGE_HISTORY = 200;
    private static final String BACKUPS_DIR_NAME = "chatsphere_backups";
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final MinecraftServer server;
    private final Map<String, ChannelEntry> channels = new LinkedHashMap<>();
    private final Map<String, String> knownPlayers = new LinkedHashMap<>();
    private final List<StoredMessage> messageHistory = new ArrayList<>();
    private boolean loaded;
    private long lastBackupTime;

    private ModServerChannels(MinecraftServer server) {
        this.server = server;
    }

    public static ModServerChannels getInstance(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> {
            ModServerChannels msc = new ModServerChannels(s);
            msc.load();
            return msc;
        });
    }

    public static void removeServer(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    public synchronized Map<String, String> getKnownPlayers() {
        return new LinkedHashMap<>(knownPlayers);
    }

    public synchronized void learnPlayerName(String uuid, String name) {
        if (uuid != null && name != null && !name.isEmpty() && !knownPlayers.containsKey(uuid)) {
            knownPlayers.put(uuid, name);
            save();
        }
    }

    public synchronized List<ChannelEntry> getAllChannels() {
        return new ArrayList<>(channels.values());
    }

    public synchronized ChannelEntry getChannel(String id) {
        return channels.get(id);
    }

    public synchronized List<ClientboundPublicChannelListPayload.PublicChannelEntry> getPublicChannels() {
        if (!ModServerConfig.CONFIG.exploreEnabled.get()) return List.of();
        int minMembers = ModServerConfig.CONFIG.exploreMinMembers.get();
        List<ClientboundPublicChannelListPayload.PublicChannelEntry> result = new ArrayList<>();
        for (ChannelEntry e : channels.values()) {
            if (!e.isPublic() || !e.showInExplore()) continue;
            if (e.members().size() < minMembers) continue;
            int onlineCount = 0;
            for (String uid : e.members()) {
                try {
                    if (server.getPlayerList().getPlayer(UUID.fromString(uid)) != null)
                        onlineCount++;
                } catch (Exception ignored) {}
            }
            String dn = e.displayName() != null && !e.displayName().isEmpty()
                    ? e.displayName() : e.id();
            result.add(new ClientboundPublicChannelListPayload.PublicChannelEntry(
                    e.id(), dn, e.description(),
                    e.members().size(), onlineCount, e.inviteCode()));
        }
        return result;
    }

    public synchronized void createChannel(String id, UUID ownerUuid, boolean isPublic, boolean showInExplore) {
        if (channels.containsKey(id)) return;
        String ownerStr = ownerUuid != null ? ownerUuid.toString() : "";
        List<String> members = new ArrayList<>();
        List<String> admins = new ArrayList<>();
        if (ownerUuid != null) {
            members.add(ownerStr);
            admins.add(ownerStr);
        }
        ChannelEntry entry = new ChannelEntry(id, ownerStr, isPublic, "", "",
                admins, new ArrayList<>(), new ArrayList<>(), members, generateInviteCode(), showInExplore, new ArrayList<>());
        channels.put(id, entry);
        save();
        broadcastSync();
    }

    public synchronized void updateChannelConfig(String channelId, boolean isPublic, String description, String displayName,
                                                  List<String> admins, List<String> mutedPlayers,
                                                  List<String> invitedPlayers, String inviteCode, UUID requester,
                                                  boolean showInExplore) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!entry.owner().equals(reqStr) && !entry.admins().contains(reqStr)) return;
        String newCode = (inviteCode != null && !inviteCode.isEmpty()) ? inviteCode : entry.inviteCode();
        ChannelEntry updated = new ChannelEntry(channelId, entry.owner(), isPublic, description, displayName,
                new ArrayList<>(admins), new ArrayList<>(mutedPlayers), new ArrayList<>(invitedPlayers),
                new ArrayList<>(entry.members()), newCode, showInExplore, entry.voiceRooms());
        channels.put(channelId, updated);
        save();
        broadcastSync();
    }

    public synchronized boolean removeChannel(String channelId, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return false;
        if (!entry.owner().equals(requester.toString())) return false;
        channels.remove(channelId);
        save();
        broadcastSync();
        return true;
    }

    public void sendToPlayer(ServerPlayer player) {
        List<ChannelEntry> list = getAllChannels().stream()
                .filter(e -> e.members().contains(player.getUUID().toString())
                    || (ModServerConfig.CONFIG.syncDefaultChannel.get() && DEFAULT_CHANNEL_ID.equals(e.id())))
                .collect(Collectors.toList());
        if (!list.isEmpty()) {
            collectOnlinePlayerNames();
            Map<String, String> kp = getKnownPlayers();
            player.connection.send(new ClientboundCustomPayloadPacket(
                    new ClientboundChannelSyncPayload(list, kp)));
        }
    }

    public void sendMessagesToPlayer(ServerPlayer player) {
        List<StoredMessage> msgs;
        synchronized (messageHistory) {
            if (messageHistory.isEmpty()) return;
            msgs = new ArrayList<>();
            String playerUuid = player.getUUID().toString();
            for (StoredMessage m : messageHistory) {
                if ("COMMAND".equals(m.conversationType())) {
                    UUID suid = m.senderUuid();
                    if (suid != null && !suid.equals(Util.NIL_UUID) && !suid.toString().equals(playerUuid)) continue;
                } else if ("PRIVATE".equals(m.conversationType())) {
                    String convId = m.conversationId();
                    boolean isForPlayer = m.senderUuid().toString().equals(playerUuid);
                    if (!isForPlayer) {
                        if (convId != null && convId.contains(":")) {
                            isForPlayer = convId.startsWith(playerUuid + ":") || convId.endsWith(":" + playerUuid);
                        } else {
                            isForPlayer = convId != null && convId.equals(playerUuid);
                        }
                    }
                    if (!isForPlayer) continue;
                } else if (!ModServerConfig.CONFIG.channelHistoryEnabled.get()) {
                    continue;
                }
                msgs.add(m);
            }
            if (msgs.isEmpty()) return;
        }
        player.connection.send(new ClientboundCustomPayloadPacket(
                new ClientboundMessageSyncPayload(msgs)));
    }

    public synchronized String joinByCode(String inviteCode, UUID playerUuid) {
        for (ChannelEntry entry : channels.values()) {
            if (entry.inviteCode().equalsIgnoreCase(inviteCode)) {
                String puid = playerUuid.toString();
                if (entry.members().contains(puid)) return "already_member";
                List<String> newMembers = new ArrayList<>(entry.members());
                newMembers.add(puid);
                ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                        entry.description(), entry.displayName(),
                        new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                        new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode(), entry.showInExplore(), entry.voiceRooms());
                channels.put(entry.id(), updated);
                save();
                broadcastSync();
                return "success";
            }
        }
        return "not_found";
    }

    public void addChatMessage(String senderName, UUID senderUuid, String content,
                                 String conversationId, String conversationType,
                                 String replyContent, String replySender,
                                 String itemNbt) {
        if (senderUuid != null && !senderUuid.equals(Util.NIL_UUID) && senderName != null && !senderName.isEmpty()) {
            learnPlayerName(senderUuid.toString(), senderName);
        }
        StoredMessage msg = new StoredMessage(senderName, senderUuid, content, System.currentTimeMillis(),
                conversationId, conversationType, replyContent, replySender, itemNbt);
        synchronized (messageHistory) {
            messageHistory.add(msg);
            if (messageHistory.size() > MAX_MESSAGE_HISTORY) {
                messageHistory.remove(0);
            }
        }
        if ("CHANNEL".equals(conversationType) && senderUuid != null) {
            addMemberToChannel(conversationId, senderUuid.toString());
        }
        saveMessages();
    }

    public synchronized void addMemberToChannel(String channelId, String playerUuid) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        List<String> newMembers = new ArrayList<>(entry.members());
        if (!newMembers.contains(playerUuid)) {
            newMembers.add(playerUuid);
            ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                    entry.description(), entry.displayName(),
                    new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                    new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode(), entry.showInExplore(),
                    entry.voiceRooms());
            channels.put(channelId, updated);
            save();
            broadcastSync();
        }
    }

    public synchronized void createVoiceRoom(String channelId, String roomName, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!entry.owner().equals(reqStr) && !entry.admins().contains(reqStr)) return;
        String finalName = roomName.trim();
        if (finalName.isEmpty() || finalName.length() > 32) return;
        List<VoiceRoom> rooms = new ArrayList<>(entry.voiceRooms());
        for (VoiceRoom vr : rooms) {
            if (vr.name.equals(finalName)) return;
        }
        rooms.add(new VoiceRoom(finalName, new ArrayList<>()));
        ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()), new ArrayList<>(entry.members()),
                entry.inviteCode(), entry.showInExplore(), rooms);
        channels.put(channelId, updated);
        save();
        broadcastSync();
    }

    public synchronized void deleteVoiceRoom(String channelId, String roomName, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!entry.owner().equals(reqStr) && !entry.admins().contains(reqStr)) return;
        List<VoiceRoom> rooms = new ArrayList<>(entry.voiceRooms());
        rooms.removeIf(vr -> vr.name.equals(roomName));
        ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()), new ArrayList<>(entry.members()),
                entry.inviteCode(), entry.showInExplore(), rooms);
        channels.put(channelId, updated);
        save();
        broadcastSync();
    }

    public synchronized void joinVoiceRoom(String channelId, String roomName, UUID playerUuid) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null || playerUuid == null) return;
        String puid = playerUuid.toString();
        if (!entry.members().contains(puid)) return;
        List<VoiceRoom> rooms = new ArrayList<>(entry.voiceRooms());
        for (int i = 0; i < rooms.size(); i++) {
            VoiceRoom vr = rooms.get(i);
            List<String> newMembers = new ArrayList<>(vr.members);
            if (vr.name.equals(roomName)) {
                if (newMembers.contains(puid)) return;
                newMembers.add(puid);
                rooms.set(i, new VoiceRoom(vr.name, newMembers));
            } else {
                newMembers.remove(puid);
                rooms.set(i, new VoiceRoom(vr.name, newMembers));
            }
        }
        ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()), new ArrayList<>(entry.members()),
                entry.inviteCode(), entry.showInExplore(), rooms);
        channels.put(channelId, updated);
        save();
        broadcastSync();
        cn.sarskin.ChatSphere.client.voice.VoiceIntegration.joinVoiceRoom(channelId, roomName, playerUuid);
    }

    public synchronized void leaveVoiceRoom(String channelId, String roomName, UUID playerUuid) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null || playerUuid == null) return;
        String puid = playerUuid.toString();
        List<VoiceRoom> rooms = new ArrayList<>(entry.voiceRooms());
        for (int i = 0; i < rooms.size(); i++) {
            VoiceRoom vr = rooms.get(i);
            if (vr.name.equals(roomName) && vr.members.contains(puid)) {
                List<String> newMembers = new ArrayList<>(vr.members);
                newMembers.remove(puid);
                rooms.set(i, new VoiceRoom(vr.name, newMembers));
                ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                        entry.description(), entry.displayName(),
                        new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                        new ArrayList<>(entry.invitedPlayers()), new ArrayList<>(entry.members()),
                        entry.inviteCode(), entry.showInExplore(), rooms);
                channels.put(channelId, updated);
                save();
                broadcastSync();
                cn.sarskin.ChatSphere.client.voice.VoiceIntegration.leaveVoiceRoom(channelId, roomName, playerUuid);
                return;
            }
        }
    }

    public synchronized void toggleMute(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!entry.owner().equals(reqStr) && !entry.admins().contains(reqStr)) return;
        List<String> newMuted = new ArrayList<>(entry.mutedPlayers());
        if (newMuted.contains(targetUuid)) newMuted.remove(targetUuid);
        else newMuted.add(targetUuid);
        channels.put(channelId, new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()), newMuted,
                new ArrayList<>(entry.invitedPlayers()),
                new ArrayList<>(entry.members()), entry.inviteCode(), entry.showInExplore(), entry.voiceRooms()));
        save();
        broadcastSync();
    }

    public synchronized void toggleAdmin(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!entry.owner().equals(reqStr)) return;
        if (entry.owner().equals(targetUuid)) return;
        List<String> newAdmins = new ArrayList<>(entry.admins());
        if (newAdmins.contains(targetUuid)) newAdmins.remove(targetUuid);
        else newAdmins.add(targetUuid);
        channels.put(channelId, new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(), newAdmins,
                new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()),
                new ArrayList<>(entry.members()), entry.inviteCode(), entry.showInExplore(), entry.voiceRooms()));
        save();
        broadcastSync();
    }

    public synchronized void toggleInvite(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        List<String> newInvited = new ArrayList<>(entry.invitedPlayers());
        if (newInvited.contains(targetUuid)) newInvited.remove(targetUuid);
        else newInvited.add(targetUuid);
        channels.put(channelId, new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()),
                new ArrayList<>(entry.mutedPlayers()), newInvited,
                new ArrayList<>(entry.members()), entry.inviteCode(), entry.showInExplore(), entry.voiceRooms()));
        save();
        broadcastSync();
    }

    public synchronized void kickMember(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null || requester == null) return;
        String reqStr = requester.toString();
        if (reqStr.equals(targetUuid)) return;
        if (!entry.owner().equals(reqStr) && !entry.admins().contains(reqStr)) return;
        if (!entry.owner().equals(reqStr) && entry.owner().equals(targetUuid)) return;
        if (!entry.owner().equals(reqStr) && !entry.owner().equals(targetUuid) && entry.admins().contains(targetUuid)) return;
        if (!entry.members().contains(targetUuid)) return;
        List<String> newMembers = new ArrayList<>(entry.members());
        newMembers.remove(targetUuid);
        List<String> newAdmins = new ArrayList<>(entry.admins());
        newAdmins.remove(targetUuid);
        List<String> newMuted = new ArrayList<>(entry.mutedPlayers());
        newMuted.remove(targetUuid);
        channels.put(channelId, new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(), newAdmins, newMuted,
                new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode(), entry.showInExplore(), entry.voiceRooms()));
        save();
        broadcastSync();
    }

    public synchronized void leaveChannel(String channelId, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null || requester == null) return;
        String reqStr = requester.toString();
        if (entry.owner().equals(reqStr)) return;
        List<String> newMembers = new ArrayList<>(entry.members());
        if (!newMembers.remove(reqStr)) return;
        List<String> newAdmins = new ArrayList<>(entry.admins());
        newAdmins.remove(reqStr);
        channels.put(channelId, new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(), newAdmins,
                new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode(), entry.showInExplore(), entry.voiceRooms()));
        save();
        broadcastSync();
    }

    public void addCommandMessage(String senderName, UUID senderUuid, String commandText) {
        if (commandText.isEmpty()) return;
        addChatMessage(senderName, senderUuid, commandText,
                "__commands__",
                "COMMAND", "", "", "");
    }

    public List<StoredMessage> getRecentMessages(int count) {
        synchronized (messageHistory) {
            if (messageHistory.isEmpty()) return List.of();
            int from = Math.max(0, messageHistory.size() - count);
            return List.copyOf(messageHistory.subList(from, messageHistory.size()));
        }
    }

    private void collectOnlinePlayerNames() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            knownPlayers.putIfAbsent(p.getUUID().toString(), p.getName().getString());
        }
    }

    private void broadcastSync() {
        collectOnlinePlayerNames();
        Map<String, String> kp = getKnownPlayers();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            List<ChannelEntry> playerChannels = channels.values().stream()
                    .filter(e -> e.members().contains(p.getUUID().toString()))
                    .collect(Collectors.toList());
            p.connection.send(new ClientboundCustomPayloadPacket(
                    new ClientboundChannelSyncPayload(playerChannels, kp)));
        }
    }

    private Path getDataPath() {
        return ModStoragePaths.getServerDataDir().resolve("channels.json");
    }

    private Path getMessagesPath() {
        return ModStoragePaths.getServerDataDir().resolve("messages.json");
    }

    public synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path path = getDataPath();
        if (!Files.exists(path)) {
            channels.clear();
            channels.put(DEFAULT_CHANNEL_ID, new ChannelEntry(DEFAULT_CHANNEL_ID, "", true, "", "",
                    List.of(), List.of(), List.of(), List.of(), generateInviteCode(), true, List.of()));
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;
            channels.clear();
            if (obj.has("channels")) {
                JsonArray arr = obj.getAsJsonArray("channels");
                for (var el : arr) {
                    JsonObject c = el.getAsJsonObject();
                    ChannelEntry entry = new ChannelEntry(
                            c.get("id").getAsString(),
                            c.get("owner").getAsString(),
                            c.get("isPublic").getAsBoolean(),
                            c.has("description") ? c.get("description").getAsString() : "",
                            c.has("displayName") ? c.get("displayName").getAsString() : "",
                            readStringList(c, "admins"),
                            readStringList(c, "mutedPlayers"),
                            readStringList(c, "invitedPlayers"),
                            readStringList(c, "members"),
                            c.has("inviteCode") ? c.get("inviteCode").getAsString() : generateInviteCode(),
                            !c.has("showInExplore") || c.get("showInExplore").getAsBoolean(),
                            readVoiceRooms(c)
                    );
                    channels.put(entry.id(), entry);
                }
            }
            knownPlayers.clear();
            if (obj.has("knownPlayers")) {
                JsonObject kpObj = obj.getAsJsonObject("knownPlayers");
                for (String key : kpObj.keySet()) {
                    knownPlayers.put(key, kpObj.get(key).getAsString());
                }
            }
            if (!channels.containsKey(DEFAULT_CHANNEL_ID)) {
                channels.put(DEFAULT_CHANNEL_ID, new ChannelEntry(DEFAULT_CHANNEL_ID, "", true, "", "",
                        List.of(), List.of(), List.of(), List.of(), generateInviteCode(), true, List.of()));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load server channels", e);
        }
        loadMessages();
    }

    private void loadMessages() {
        Path path = getMessagesPath();
        if (!Files.exists(path)) return;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null || !obj.has("messages")) return;
            JsonArray arr = obj.getAsJsonArray("messages");
            synchronized (messageHistory) {
                messageHistory.clear();
                for (var el : arr) {
                    JsonObject m = el.getAsJsonObject();
                    StoredMessage sm = new StoredMessage(
                            m.get("senderName").getAsString(),
                            UUID.fromString(m.get("senderUuid").getAsString()),
                            m.get("content").getAsString(),
                            m.get("timestamp").getAsLong(),
                            m.has("conversationId") ? m.get("conversationId").getAsString() : DEFAULT_CHANNEL_ID,
                            m.has("conversationType") ? m.get("conversationType").getAsString() : "CHANNEL",
                            m.has("replyContent") ? m.get("replyContent").getAsString() : "",
                            m.has("replySender") ? m.get("replySender").getAsString() : "",
                            m.has("itemNbt") ? m.get("itemNbt").getAsString() : ""
                    );
                    messageHistory.add(sm);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load server messages", e);
        }
    }

    private void saveMessages() {
        Path path = getMessagesPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            synchronized (messageHistory) {
                for (StoredMessage m : messageHistory) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("senderName", m.senderName());
                    obj.addProperty("senderUuid", m.senderUuid().toString());
                    obj.addProperty("content", m.content());
                    obj.addProperty("timestamp", m.timestamp());
                    obj.addProperty("conversationId", m.conversationId());
                    obj.addProperty("conversationType", m.conversationType());
                    if (m.replyContent() != null && !m.replyContent().isEmpty()) {
                        obj.addProperty("replyContent", m.replyContent());
                        obj.addProperty("replySender", m.replySender());
                    }
                    if (m.itemNbt() != null && !m.itemNbt().isEmpty()) {
                        obj.addProperty("itemNbt", m.itemNbt());
                    }
                    arr.add(obj);
                }
            }
            root.add("messages", arr);
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            performBackupIfNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to save server messages", e);
        }
    }

    public synchronized void save() {
        Path path = getDataPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (ChannelEntry e : channels.values()) {
                JsonObject c = new JsonObject();
                c.addProperty("id", e.id());
                c.addProperty("owner", e.owner());
                c.addProperty("isPublic", e.isPublic());
                c.addProperty("description", e.description());
                c.addProperty("displayName", e.displayName());
                writeStringList(c, "admins", e.admins());
                writeStringList(c, "mutedPlayers", e.mutedPlayers());
                writeStringList(c, "invitedPlayers", e.invitedPlayers());
                writeStringList(c, "members", e.members());
                c.addProperty("inviteCode", e.inviteCode());
                c.addProperty("showInExplore", e.showInExplore());
                JsonArray vrArr = new JsonArray();
                for (VoiceRoom vr : e.voiceRooms()) {
                    JsonObject vrObj = new JsonObject();
                    vrObj.addProperty("name", vr.name());
                    JsonArray mArr = new JsonArray();
                    for (String m : vr.members()) mArr.add(m);
                    vrObj.add("members", mArr);
                    vrArr.add(vrObj);
                }
                c.add("voiceRooms", vrArr);
                arr.add(c);
            }
            root.add("channels", arr);
            if (!knownPlayers.isEmpty()) {
                JsonObject kpObj = new JsonObject();
                for (Map.Entry<String, String> e : knownPlayers.entrySet()) {
                    kpObj.addProperty(e.getKey(), e.getValue());
                }
                root.add("knownPlayers", kpObj);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            performBackupIfNeeded();
        } catch (Exception e) {
            LOGGER.error("Failed to save server channels", e);
        }
    }

    private void performBackupIfNeeded() {
        int intervalMin = cn.sarskin.ChatSphere.config.ModServerConfig.CONFIG.backupIntervalMinutes.get();
        if (intervalMin <= 0) return;
        long intervalMs = intervalMin * 60 * 1000L;
        if (System.currentTimeMillis() - lastBackupTime < intervalMs) return;
        Path channelsPath = getDataPath();
        Path messagesPath = getMessagesPath();
        try {
            Path backupDir = ModStoragePaths.getServerDataDir().resolve(BACKUPS_DIR_NAME);
            Files.createDirectories(backupDir);
            String ts = LocalDateTime.now().format(BACKUP_TIMESTAMP);
            if (Files.exists(channelsPath)) {
                Files.copy(channelsPath, backupDir.resolve("channels_" + ts + ".json"), StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(messagesPath)) {
                Files.copy(messagesPath, backupDir.resolve("messages_" + ts + ".json"), StandardCopyOption.REPLACE_EXISTING);
            }
            lastBackupTime = System.currentTimeMillis();
            LOGGER.info("Created server data backup: {}", ts);
            pruneBackups(backupDir);
        } catch (Exception e) {
            LOGGER.error("Failed to perform server backup", e);
        }
    }

    private static void pruneBackups(Path backupDir) {
        try {
            if (!Files.exists(backupDir)) return;
            List<Path> sorted;
            try (var stream = Files.list(backupDir)) {
                sorted = stream
                        .filter(p -> p.toString().endsWith(".json"))
                        .sorted(Comparator.comparingLong(p -> {
                            try { return Files.getLastModifiedTime(p).toMillis(); }
                            catch (IOException e) { return 0; }
                        }))
                        .collect(Collectors.toList());
            }
            while (sorted.size() > cn.sarskin.ChatSphere.config.ModServerConfig.CONFIG.backupKeepMax.get()) {
                Path oldest = sorted.remove(0);
                Files.deleteIfExists(oldest);
                LOGGER.info("Pruned old server backup: {}", oldest);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to prune server backups", e);
        }
    }

    private static List<String> readStringList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key)) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (var el : arr) list.add(el.getAsString());
        }
        return list;
    }

    private static List<VoiceRoom> readVoiceRooms(JsonObject obj) {
        List<VoiceRoom> rooms = new ArrayList<>();
        if (obj.has("voiceRooms")) {
            JsonArray arr = obj.getAsJsonArray("voiceRooms");
            for (var el : arr) {
                JsonObject vrObj = el.getAsJsonObject();
                String name = vrObj.get("name").getAsString();
                List<String> members = new ArrayList<>();
                if (vrObj.has("members")) {
                    JsonArray mArr = vrObj.getAsJsonArray("members");
                    for (var mEl : mArr) members.add(mEl.getAsString());
                }
                rooms.add(new VoiceRoom(name, members));
            }
        }
        return rooms;
    }

    private static void writeStringList(JsonObject obj, String key, List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) arr.add(s);
        obj.add(key, arr);
    }

    public record ChannelEntry(
            String id, String owner, boolean isPublic, String description, String displayName,
            List<String> admins, List<String> mutedPlayers, List<String> invitedPlayers,
            List<String> members, String inviteCode, boolean showInExplore,
            List<VoiceRoom> voiceRooms
    ) {
        public ChannelEntry {
            if (inviteCode == null || inviteCode.isEmpty()) {
                inviteCode = generateInviteCode();
            }
            if (voiceRooms == null) {
                voiceRooms = List.of();
            }
        }
    }

    public record VoiceRoom(String name, List<String> members) {}

    private static String generateInviteCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
