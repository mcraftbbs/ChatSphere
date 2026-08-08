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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class ModServerChannels {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModServerChannels");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, ModServerChannels> INSTANCES = new ConcurrentHashMap<>();
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

    public static boolean isSubChannel(String id) {
        return id != null && id.contains("/");
    }

    public static String subParentOf(String id) {
        if (id == null) return "";
        int idx = id.lastIndexOf('/');
        return idx >= 0 ? id.substring(0, idx) : id;
    }

    public static String subNameOf(String id) {
        if (id == null) return "";
        int idx = id.lastIndexOf('/');
        return idx >= 0 ? id.substring(idx + 1) : id;
    }

    public static int channelDepth(String id) {
        if (id == null) return 0;
        int depth = 0;
        for (int i = 0; i < id.length(); i++) {
            if (id.charAt(i) == '/') depth++;
        }
        return depth;
    }

    public static boolean isValidChannelSegment(String name) {
        return name != null && !name.isEmpty() && !name.contains("/")
                && !name.equals("null") && name.length() <= 32;
    }

    public synchronized List<String> effectiveMembers(String channelId) {
        ChannelEntry e = channels.get(channelId);
        if (e == null) return List.of();
        ChannelEntry root = e;
        int guard = 0;
        while (root.parentId() != null && !root.parentId().isEmpty() && guard++ < 32) {
            ChannelEntry parent = channels.get(root.parentId());
            if (parent == null) break;
            root = parent;
        }
        return new ArrayList<>(root.members());
    }

    private ChannelEntry resolveTarget(String channelId) {
        ChannelEntry e = channels.get(channelId);
        if (e == null) return null;
        ChannelEntry root = e;
        int guard = 0;
        while (root.parentId() != null && !root.parentId().isEmpty() && guard++ < 32) {
            ChannelEntry parent = channels.get(root.parentId());
            if (parent == null) break;
            root = parent;
        }
        return root;
    }

    public synchronized boolean isMuted(String channelId, String playerUuid) {
        ChannelEntry target = resolveTarget(channelId);
        return target != null && playerUuid != null && target.mutedPlayers().contains(playerUuid);
    }

    /**
     * Resolve the actual chat target for a channel send. When the main channel's
     * chat is disabled, messages are redirected to its default sub-channel.
     * Returns null when chat is disabled and there is no usable default sub-channel.
     */
    public synchronized String resolveChatChannel(String channelId) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return null;
        if (entry.mainChatEnabled() || entry.parentId() != null && !entry.parentId().isEmpty()) {
            return channelId;
        }
        String def = entry.defaultSubChannel();
        if (def == null || def.isEmpty()) return null;
        String defId = channelId + "/" + def;
        ChannelEntry defEntry = channels.get(defId);
        if (defEntry == null) return null;
        return defId;
    }

    public synchronized boolean isOwnerOrAdmin(String channelId, String playerUuid) {
        ChannelEntry root = resolveTarget(channelId);
        if (root == null || playerUuid == null) return false;
        if (root.owner().equals(playerUuid)) return true;
        return root.admins().contains(playerUuid);
    }

    public synchronized List<ChannelEntry> sortedChannels() {
        return channels.values().stream()
                .sorted(Comparator.comparingInt(ChannelEntry::sortOrder)
                        .thenComparing(ChannelEntry::id))
                .collect(Collectors.toList());
    }

    public synchronized List<ChannelEntry> getSubChannels(String parentId) {
        return channels.values().stream()
                .filter(e -> parentId.equals(e.parentId()))
                .sorted(Comparator.comparingInt(ChannelEntry::sortOrder)
                        .thenComparing(ChannelEntry::id))
                .collect(Collectors.toList());
    }

    private void compactSortOrders() {
        List<ChannelEntry> ordered = sortedChannels();
        for (int i = 0; i < ordered.size(); i++) {
            ChannelEntry e = ordered.get(i);
            if (e.sortOrder() != i) {
                channels.put(e.id(), new ChannelEntry(e.id(), e.owner(), e.isPublic(), e.description(),
                        e.displayName(), new ArrayList<>(e.admins()), new ArrayList<>(e.mutedPlayers()),
                        new ArrayList<>(e.invitedPlayers()), new ArrayList<>(e.members()), e.inviteCode(),
                        e.showInExplore(), e.voiceRooms(), e.parentId(), i, e.mainChatEnabled(), e.defaultSubChannel()));
            }
        }
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

    public synchronized void createChannel(String id, UUID ownerUuid, boolean isPublic, boolean showInExplore,
                                           boolean mainChatEnabled, String defaultSubChannel) {
        if (channels.containsKey(id)) return;
        if (id == null || id.isEmpty()) return;
        if (isSubChannel(id)) {
            String parentId = subParentOf(id);
            String childName = subNameOf(id);
            ChannelEntry parent = channels.get(parentId);
            if (parent == null) return;
            String ownerStr = ownerUuid != null ? ownerUuid.toString() : "";
            if (!isOwnerOrAdmin(parentId, ownerStr)) return;
            if (!isValidChannelSegment(childName)) return;
            if (channels.containsKey(id)) return;
            int maxOrder = channels.values().stream()
                    .filter(e -> parentId.equals(e.parentId()))
                    .mapToInt(ChannelEntry::sortOrder)
                    .max().orElse(-1);
            ChannelEntry child = new ChannelEntry(id, parent.owner(), false, "", "",
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                    generateInviteCode(), false, new ArrayList<>(), parentId, maxOrder + 1,
                    true, "");
            channels.put(id, child);
            save();
            broadcastSync();
            return;
        }
        if (!isValidChannelSegment(id.startsWith("#") ? id.substring(1) : id)) return;
        if (defaultSubChannel != null && defaultSubChannel.contains("/")) return;
        String ownerStr = ownerUuid != null ? ownerUuid.toString() : "";
        List<String> members = new ArrayList<>();
        List<String> admins = new ArrayList<>();
        if (ownerUuid != null) {
            members.add(ownerStr);
            admins.add(ownerStr);
        }
        int maxOrder = channels.values().stream()
                .filter(e -> e.parentId() == null || e.parentId().isEmpty())
                .mapToInt(ChannelEntry::sortOrder)
                .max().orElse(-1);
        ChannelEntry entry = new ChannelEntry(id, ownerStr, isPublic, "", "",
                admins, new ArrayList<>(), new ArrayList<>(), members, generateInviteCode(), showInExplore, new ArrayList<>(), "", maxOrder + 1,
                mainChatEnabled, defaultSubChannel != null ? defaultSubChannel : "");
        channels.put(id, entry);
        save();
        broadcastSync();
    }

    public synchronized void updateChannelConfig(String channelId, boolean isPublic, String description, String displayName,
                                                  List<String> admins, List<String> mutedPlayers,
                                                  List<String> invitedPlayers, String inviteCode, UUID requester,
                                                  boolean showInExplore, boolean mainChatEnabled, String defaultSubChannel) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return;
        if (defaultSubChannel != null && defaultSubChannel.contains("/")) return;
        String reqStr = requester.toString();
        if (!isOwnerOrAdmin(channelId, reqStr)) return;
        String newCode = (inviteCode != null && !inviteCode.isEmpty()) ? inviteCode : entry.inviteCode();
        boolean isSub = entry.parentId() != null && !entry.parentId().isEmpty();
        List<String> membersToStore = isSub
                ? new ArrayList<>(entry.members())
                : effectiveMembers(channelId);
        String newDefault = defaultSubChannel != null ? defaultSubChannel : entry.defaultSubChannel();
        ChannelEntry updated = new ChannelEntry(channelId, entry.owner(), isPublic, description, displayName,
                new ArrayList<>(admins), new ArrayList<>(mutedPlayers), new ArrayList<>(invitedPlayers),
                membersToStore, newCode, showInExplore, entry.voiceRooms(), entry.parentId(), entry.sortOrder(),
                mainChatEnabled, newDefault);
        channels.put(channelId, updated);
        save();
        broadcastSync();
    }

    public synchronized boolean renameChannel(String channelId, String newName, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return false;
        if (entry.parentId() == null || entry.parentId().isEmpty()) return false;
        if (!isOwnerOrAdmin(channelId, requester.toString())) return false;
        String newSegment = newName.startsWith("#") ? newName.substring(1) : newName;
        if (!isValidChannelSegment(newSegment)) return false;
        String newId = entry.parentId() + "/" + newSegment;
        if (channels.containsKey(newId)) return false;
        String oldSegment = subNameOf(channelId);
        if (!oldSegment.equals(newSegment)) {
            ChannelEntry parent = channels.get(entry.parentId());
            if (parent != null && oldSegment.equals(parent.defaultSubChannel())) {
                ChannelEntry parentUpdated = new ChannelEntry(parent.id(), parent.owner(), parent.isPublic(),
                        parent.description(), parent.displayName(), new ArrayList<>(parent.admins()),
                        new ArrayList<>(parent.mutedPlayers()), new ArrayList<>(parent.invitedPlayers()),
                        new ArrayList<>(parent.members()), parent.inviteCode(), parent.showInExplore(),
                        parent.voiceRooms(), parent.parentId(), parent.sortOrder(),
                        parent.mainChatEnabled(), newSegment);
                channels.put(parent.id(), parentUpdated);
            }
        }
        rekeyChannelTree(channelId, newId, requester, null);
        return true;
    }

    /**
     * Move a channel (with its whole subtree) under a new parent, like tree-view drag nesting.
     * channelId may be top-level (parentId empty) or a sub-channel; newParentId must be an
     * existing channel that is not the channel itself or one of its descendants.
     */
    public synchronized boolean moveChannel(String channelId, String newParentId, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null || newParentId == null || newParentId.isEmpty()) return false;
        ChannelEntry newParent = channels.get(newParentId);
        if (newParent == null) return false;
        if (channelId.equals(newParentId)) return false;
        if (channelDepth(newParentId) > 1) return false; // parents limited to top-level or level-1
        if (channelId.startsWith(newParentId + "/")) return false; // cannot move into own subtree
        String reqStr = requester != null ? requester.toString() : "";
        if (!isOwnerOrAdmin(channelId, reqStr) || !isOwnerOrAdmin(newParentId, reqStr)) return false;
        if (newParentId.startsWith(channelId + "/")) return false; // cannot move into own subtree
        String newSegment = subNameOf(channelId);
        if (newSegment.startsWith("#")) newSegment = newSegment.substring(1);
        if (!isValidChannelSegment(newSegment)) return false;
        String newId = newParentId + "/" + newSegment;
        if (channels.containsKey(newId)) return false;
        int newOrder = channels.values().stream()
                .filter(e -> newParentId.equals(e.parentId()))
                .mapToInt(ChannelEntry::sortOrder)
                .max().orElse(-1) + 1;
        rekeyChannelTree(channelId, newId, requester, newParentId, newOrder);
        return true;
    }

    /**
     * Re-key a channel id and its whole descendant subtree, re-key message history,
     * save, broadcast sync and renamed events. New id must not exist yet.
     */
    private void rekeyChannelTree(String oldId, String newId, UUID requester, String newParentId) {
        rekeyChannelTree(oldId, newId, requester, newParentId, null);
    }

    private void rekeyChannelTree(String oldId, String newId, UUID requester, String newParentId, Integer newSortOrder) {
        ChannelEntry entry = channels.get(oldId);
        if (entry == null) return;
        String effectiveParent = newParentId != null ? newParentId : entry.parentId();
        int sortOrder = newSortOrder != null ? newSortOrder : entry.sortOrder();
        ChannelEntry renamed = new ChannelEntry(newId, entry.owner(), entry.isPublic(), entry.description(),
                entry.displayName(), new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()), new ArrayList<>(entry.members()), entry.inviteCode(),
                entry.showInExplore(), entry.voiceRooms(), effectiveParent, sortOrder, entry.mainChatEnabled(), entry.defaultSubChannel());
        channels.remove(oldId);
        channels.put(newId, renamed);
        Map<String, String> oldToNew = new HashMap<>();
        oldToNew.put(oldId, newId);
        String oldPrefix = oldId + "/";
        String newPrefix = newId + "/";
        for (ChannelEntry sub : channels.values().toArray(new ChannelEntry[0])) {
            if (sub.id().startsWith(oldPrefix)) {
                String childNewId = newPrefix + sub.id().substring(oldPrefix.length());
                ChannelEntry childUpdated = new ChannelEntry(childNewId, sub.owner(), sub.isPublic(), sub.description(),
                        sub.displayName(), new ArrayList<>(sub.admins()), new ArrayList<>(sub.mutedPlayers()),
                        new ArrayList<>(sub.invitedPlayers()), new ArrayList<>(sub.members()), sub.inviteCode(),
                        sub.showInExplore(), sub.voiceRooms(), subParentOf(childNewId), sub.sortOrder(), sub.mainChatEnabled(), sub.defaultSubChannel());
                channels.remove(sub.id());
                channels.put(childNewId, childUpdated);
                oldToNew.put(sub.id(), childNewId);
            }
        }
        rekeyMessageHistory(oldToNew);
        save();
        broadcastSync();
        for (Map.Entry<String, String> en : oldToNew.entrySet()) {
            cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload renamedPayload =
                    new cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload(en.getKey(), en.getValue());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.connection.send(new ClientboundCustomPayloadPacket(renamedPayload));
            }
        }
    }

    private void rekeyMessageHistory(Map<String, String> oldToNew) {
        synchronized (messageHistory) {
            for (int i = 0; i < messageHistory.size(); i++) {
                StoredMessage m = messageHistory.get(i);
                String mapped = oldToNew.get(m.conversationId());
                if (mapped != null) {
                    messageHistory.set(i, new StoredMessage(m.senderName(), m.senderUuid(), m.content(),
                            m.timestamp(), mapped, m.conversationType(), m.replyContent(), m.replySender(), m.itemNbt()));
                }
            }
        }
        saveMessages();
    }

    public synchronized boolean reorderChannels(List<String> orderedIds, UUID requester) {
        if (orderedIds == null || orderedIds.isEmpty()) return false;
        String groupParent = orderedIds.get(0).contains("/")
                ? subParentOf(orderedIds.get(0)) : "";
        String reqStr = requester != null ? requester.toString() : "";
        boolean hasPermission = false;
        for (int i = 0; i < orderedIds.size(); i++) {
            ChannelEntry e = channels.get(orderedIds.get(i));
            if (e == null) return false;
            String expected = groupParent;
            String actual = e.parentId() == null ? "" : e.parentId();
            if (!expected.equals(actual)) return false;
            if (isOwnerOrAdmin(orderedIds.get(i), reqStr)) hasPermission = true;
        }
        if (!hasPermission) return false;
        int base = Integer.MAX_VALUE;
        for (String id : orderedIds) {
            ChannelEntry e = channels.get(id);
            if (e != null) base = Math.min(base, e.sortOrder());
        }
        if (base == Integer.MAX_VALUE) return false;
        for (int i = 0; i < orderedIds.size(); i++) {
            ChannelEntry e = channels.get(orderedIds.get(i));
            ChannelEntry updated = new ChannelEntry(e.id(), e.owner(), e.isPublic(), e.description(),
                    e.displayName(), new ArrayList<>(e.admins()), new ArrayList<>(e.mutedPlayers()),
                    new ArrayList<>(e.invitedPlayers()), new ArrayList<>(e.members()), e.inviteCode(),
                    e.showInExplore(), e.voiceRooms(), e.parentId(), base + i, e.mainChatEnabled(), e.defaultSubChannel());
            channels.put(e.id(), updated);
        }
        save();
        broadcastSync();
        return true;
    }

    public synchronized boolean removeChannel(String channelId, UUID requester) {
        ChannelEntry entry = channels.get(channelId);
        if (entry == null) return false;
        String reqStr = requester != null ? requester.toString() : "";
        boolean isSub = entry.parentId() != null && !entry.parentId().isEmpty();
        if (isSub) {
            if (!isOwnerOrAdmin(channelId, reqStr)) return false;
        } else {
            if (!entry.owner().equals(reqStr)) return false;
        }
        List<String> cascade = new ArrayList<>();
        String prefix = channelId + "/";
        for (ChannelEntry e : channels.values()) {
            if (e.id().startsWith(prefix) || e.id().equals(channelId)) cascade.add(e.id());
        }
        for (String id : cascade) channels.remove(id);
        compactSortOrders();
        save();
        broadcastSync();
        return true;
    }

    public void sendToPlayer(ServerPlayer player) {
        List<ChannelEntry> list = sortedChannels().stream()
                .filter(e -> effectiveMembers(e.id()).contains(player.getUUID().toString())
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
                } else if ("CHANNEL".equals(m.conversationType())) {
                    if (!ModServerConfig.CONFIG.channelHistoryEnabled.get()) {
                        continue;
                    }
                    if (!effectiveMembers(m.conversationId()).contains(playerUuid)) {
                        continue;
                    }
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
                ChannelEntry target = resolveTarget(entry.id());
                if (target == null) return "not_found";
                String puid = playerUuid.toString();
                if (target.members().contains(puid)) return "already_member";
                List<String> newMembers = new ArrayList<>(target.members());
                newMembers.add(puid);
                ChannelEntry updated = new ChannelEntry(target.id(), target.owner(), target.isPublic(),
                        target.description(), target.displayName(),
                        new ArrayList<>(target.admins()), new ArrayList<>(target.mutedPlayers()),
                        new ArrayList<>(target.invitedPlayers()), newMembers, target.inviteCode(), target.showInExplore(), target.voiceRooms(),
                        target.parentId(), target.sortOrder(), target.mainChatEnabled(), target.defaultSubChannel());
                channels.put(target.id(), updated);
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
            int cap = ModServerConfig.CONFIG.maxChatHistory.get();
            while (messageHistory.size() > cap) {
                messageHistory.remove(0);
            }
        }
        if ("CHANNEL".equals(conversationType) && senderUuid != null) {
            addMemberToChannel(conversationId, senderUuid.toString());
        }
        saveMessages();
    }

    public synchronized void addMemberToChannel(String channelId, String playerUuid) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null) return;
        List<String> newMembers = new ArrayList<>(entry.members());
        if (!newMembers.contains(playerUuid)) {
            newMembers.add(playerUuid);
            ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                    entry.description(), entry.displayName(),
                    new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                    new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode(), entry.showInExplore(),
                    entry.voiceRooms(), entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel());
            channels.put(entry.id(), updated);
            save();
            broadcastSync();
        }
    }

    public synchronized void createVoiceRoom(String channelId, String roomName, UUID requester) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!isOwnerOrAdmin(channelId, reqStr)) return;
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
                entry.inviteCode(), entry.showInExplore(), rooms, entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel());
        channels.put(entry.id(), updated);
        save();
        broadcastSync();
    }

    public synchronized void deleteVoiceRoom(String channelId, String roomName, UUID requester) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!isOwnerOrAdmin(channelId, reqStr)) return;
        List<VoiceRoom> rooms = new ArrayList<>(entry.voiceRooms());
        rooms.removeIf(vr -> vr.name.equals(roomName));
        ChannelEntry updated = new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()), new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()), new ArrayList<>(entry.members()),
                entry.inviteCode(), entry.showInExplore(), rooms, entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel());
        channels.put(entry.id(), updated);
        save();
        broadcastSync();
    }

    public synchronized void joinVoiceRoom(String channelId, String roomName, UUID playerUuid) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null || playerUuid == null) return;
        String puid = playerUuid.toString();
        if (!effectiveMembers(channelId).contains(puid)) return;
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
                entry.inviteCode(), entry.showInExplore(), rooms, entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel());
        channels.put(entry.id(), updated);
        save();
        broadcastSync();
        cn.sarskin.ChatSphere.client.voice.VoiceIntegration.joinVoiceRoom(channelId, roomName, playerUuid);
    }

    public synchronized void leaveVoiceRoom(String channelId, String roomName, UUID playerUuid) {
        ChannelEntry entry = resolveTarget(channelId);
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
                        entry.inviteCode(), entry.showInExplore(), rooms, entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel());
                channels.put(entry.id(), updated);
                save();
                broadcastSync();
                cn.sarskin.ChatSphere.client.voice.VoiceIntegration.leaveVoiceRoom(channelId, roomName, playerUuid);
                return;
            }
        }
    }

    public synchronized void toggleMute(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!isOwnerOrAdmin(channelId, reqStr)) return;
        List<String> newMuted = new ArrayList<>(entry.mutedPlayers());
        if (newMuted.contains(targetUuid)) newMuted.remove(targetUuid);
        else newMuted.add(targetUuid);
        channels.put(entry.id(), new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()), newMuted,
                new ArrayList<>(entry.invitedPlayers()),
                new ArrayList<>(entry.members()), entry.inviteCode(), entry.showInExplore(), entry.voiceRooms(), entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel()));
        save();
        broadcastSync();
    }

    public synchronized void toggleAdmin(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null) return;
        String reqStr = requester.toString();
        if (!entry.owner().equals(reqStr)) return;
        if (entry.owner().equals(targetUuid)) return;
        List<String> newAdmins = new ArrayList<>(entry.admins());
        if (newAdmins.contains(targetUuid)) newAdmins.remove(targetUuid);
        else newAdmins.add(targetUuid);
        channels.put(entry.id(), new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(), newAdmins,
                new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()),
                new ArrayList<>(entry.members()), entry.inviteCode(), entry.showInExplore(), entry.voiceRooms(), entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel()));
        save();
        broadcastSync();
    }

    public synchronized void toggleInvite(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null) return;
        List<String> newInvited = new ArrayList<>(entry.invitedPlayers());
        if (newInvited.contains(targetUuid)) newInvited.remove(targetUuid);
        else newInvited.add(targetUuid);
        channels.put(entry.id(), new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(),
                new ArrayList<>(entry.admins()),
                new ArrayList<>(entry.mutedPlayers()), newInvited,
                new ArrayList<>(entry.members()), entry.inviteCode(), entry.showInExplore(), entry.voiceRooms(), entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel()));
        save();
        broadcastSync();
    }

    public synchronized void kickMember(String channelId, String targetUuid, UUID requester) {
        ChannelEntry entry = resolveTarget(channelId);
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
        channels.put(entry.id(), new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(), newAdmins, newMuted,
                new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode(), entry.showInExplore(), entry.voiceRooms(), entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel()));
        save();
        broadcastSync();
    }

    public synchronized void leaveChannel(String channelId, UUID requester) {
        ChannelEntry entry = resolveTarget(channelId);
        if (entry == null || requester == null) return;
        String reqStr = requester.toString();
        if (entry.owner().equals(reqStr)) return;
        List<String> newMembers = new ArrayList<>(entry.members());
        if (!newMembers.remove(reqStr)) return;
        List<String> newAdmins = new ArrayList<>(entry.admins());
        newAdmins.remove(reqStr);
        channels.put(entry.id(), new ChannelEntry(entry.id(), entry.owner(), entry.isPublic(),
                entry.description(), entry.displayName(), newAdmins,
                new ArrayList<>(entry.mutedPlayers()),
                new ArrayList<>(entry.invitedPlayers()), newMembers, entry.inviteCode(), entry.showInExplore(), entry.voiceRooms(), entry.parentId(), entry.sortOrder(), entry.mainChatEnabled(), entry.defaultSubChannel()));
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
            List<ChannelEntry> playerChannels = sortedChannels().stream()
                    .filter(e -> effectiveMembers(e.id()).contains(p.getUUID().toString())
                            || (ModServerConfig.CONFIG.syncDefaultChannel.get() && DEFAULT_CHANNEL_ID.equals(e.id())))
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
                    List.of(), List.of(), List.of(), List.of(), generateInviteCode(), true, List.of(), "", 0,
                    true, ""));
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            if (obj == null) return;
            Map<String, ChannelEntry> loadedChannels = new LinkedHashMap<>();
            if (obj.has("channels")) {
                JsonArray arr = obj.getAsJsonArray("channels");
                int defaultOrder = 0;
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
                            readVoiceRooms(c),
                            c.has("parentId") ? c.get("parentId").getAsString() : "",
                            c.has("sortOrder") ? c.get("sortOrder").getAsInt() : defaultOrder,
                            !c.has("mainChatEnabled") || c.get("mainChatEnabled").getAsBoolean(),
                            c.has("defaultSubChannel") ? c.get("defaultSubChannel").getAsString() : ""
                    );
                    loadedChannels.put(entry.id(), entry);
                    defaultOrder++;
                }
            }
            Map<String, String> loadedKnownPlayers = new LinkedHashMap<>();
            if (obj.has("knownPlayers")) {
                JsonObject kpObj = obj.getAsJsonObject("knownPlayers");
                for (String key : kpObj.keySet()) {
                    loadedKnownPlayers.put(key, kpObj.get(key).getAsString());
                }
            }
            if (!loadedChannels.containsKey(DEFAULT_CHANNEL_ID)) {
                loadedChannels.put(DEFAULT_CHANNEL_ID, new ChannelEntry(DEFAULT_CHANNEL_ID, "", true, "", "",
                        List.of(), List.of(), List.of(), List.of(), generateInviteCode(), true, List.of(), "", 0,
                        true, ""));
            }
            channels.clear();
            channels.putAll(loadedChannels);
            knownPlayers.clear();
            knownPlayers.putAll(loadedKnownPlayers);
            compactSortOrders();
        } catch (Exception e) {
            LOGGER.error("Failed to load server channels (backing up corrupt file)", e);
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                LOGGER.error("Failed to back up corrupt channels file", ex);
            }
        }
        if (!channels.containsKey(DEFAULT_CHANNEL_ID)) {
            channels.put(DEFAULT_CHANNEL_ID, new ChannelEntry(DEFAULT_CHANNEL_ID, "", true, "", "",
                    List.of(), List.of(), List.of(), List.of(), generateInviteCode(), true, List.of(), "", 0,
                    true, ""));
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
            List<StoredMessage> loaded = new ArrayList<>();
            for (var el : arr) {
                try {
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
                    loaded.add(sm);
                } catch (Exception e) {
                    LOGGER.warn("Skipping corrupt stored message: {}", e.getMessage());
                }
            }
            synchronized (messageHistory) {
                messageHistory.clear();
                messageHistory.addAll(loaded);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load server messages (backing up corrupt file)", e);
            try {
                Files.move(path, path.resolveSibling(path.getFileName() + ".corrupt"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                LOGGER.error("Failed to back up corrupt messages file", ex);
            }
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
                if (e.parentId() != null && !e.parentId().isEmpty()) {
                    c.addProperty("parentId", e.parentId());
                }
                c.addProperty("sortOrder", e.sortOrder());
                c.addProperty("mainChatEnabled", e.mainChatEnabled());
                if (e.defaultSubChannel() != null && !e.defaultSubChannel().isEmpty()) {
                    c.addProperty("defaultSubChannel", e.defaultSubChannel());
                }
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

    public synchronized void flush() {
        saveMessages();
        save();
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
            List<VoiceRoom> voiceRooms, String parentId, int sortOrder,
            boolean mainChatEnabled, String defaultSubChannel
    ) {
        public ChannelEntry {
            if (inviteCode == null || inviteCode.isEmpty()) {
                inviteCode = generateInviteCode();
            }
            if (voiceRooms == null) {
                voiceRooms = List.of();
            }
            if (parentId == null) {
                parentId = "";
            }
            if (defaultSubChannel == null) {
                defaultSubChannel = "";
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
