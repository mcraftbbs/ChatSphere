package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ClientboundVoicePacket;
import cn.sarskin.ChatSphere.storage.ModStoragePaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ModVoiceStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-VoiceStore");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<MinecraftServer, ModVoiceStorage> INSTANCES = new ConcurrentHashMap<>();

    private final MinecraftServer server;
    private final Path storageDir;
    private final List<StoredVoice> undelivered = new ArrayList<>();
    private ScheduledExecutorService cleaner;

    public record StoredVoice(
            UUID voiceMessageId,
            String senderUuid,
            String conversationId,
            String conversationType,
            long timestamp,
            int frameCount,
            byte[] audioData
    ) {}

    private ModVoiceStorage(MinecraftServer server) {
        this.server = server;
        this.storageDir = ModStoragePaths.getServerDataDir().resolve("voice_undelivered");
    }

    public static ModVoiceStorage getInstance(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> {
            ModVoiceStorage vs = new ModVoiceStorage(s);
            vs.load();
            vs.startCleaner();
            return vs;
        });
    }

    public static void removeServer(MinecraftServer server) {
        ModVoiceStorage vs = INSTANCES.remove(server);
        if (vs != null) vs.stopCleaner();
    }

    public synchronized void store(UUID voiceMessageId, String senderUuid, String conversationId,
                                    String conversationType, int frameCount, byte[] audioData) {
        if (!ModServerConfig.CONFIG.voiceOfflineStorage.get()) return;
        StoredVoice sv = new StoredVoice(voiceMessageId, senderUuid, conversationId, conversationType,
                System.currentTimeMillis(), frameCount, audioData);
        String recipientUuid = resolveRecipient(senderUuid, conversationId);
        if (recipientUuid != null) {
            long count = undelivered.stream().filter(v -> {
                String r = resolveRecipient(v.senderUuid, v.conversationId);
                return recipientUuid.equals(r);
            }).count();
            int max = ModServerConfig.CONFIG.voiceOfflineMaxPerPlayer.get();
            if (count >= max) return;
        }
        undelivered.add(sv);
        // Evict oldest beyond the configured cap (bounded disk usage).
        int maxStored = ModServerConfig.CONFIG.voiceStorageMax.get();
        while (undelivered.size() > maxStored) {
            undelivered.remove(0);
        }
        save();
    }

    public synchronized void deliverToPlayer(ServerPlayer player) {
        String puid = player.getUUID().toString();
        List<StoredVoice> toDeliver = new ArrayList<>();
        Iterator<StoredVoice> it = undelivered.iterator();
        while (it.hasNext()) {
            StoredVoice sv = it.next();
            String recipient = resolveRecipient(sv.senderUuid, sv.conversationId);
            if (puid.equals(recipient)) {
                toDeliver.add(sv);
                it.remove();
            }
        }
        if (toDeliver.isEmpty()) return;
        for (StoredVoice sv : toDeliver) {
            UUID sender;
            try {
                sender = UUID.fromString(sv.senderUuid);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Skipping voice with corrupt sender uuid: {}", sv.senderUuid);
                continue;
            }
            ClientboundVoicePacket relay = new ClientboundVoicePacket(
                    sv.voiceMessageId != null ? sv.voiceMessageId : UUID.randomUUID(),
                    sender,
                    sv.conversationId, sv.conversationType,
                    sv.frameCount, sv.audioData);
            player.connection.send(new ClientboundCustomPayloadPacket(ClientboundVoicePacket.ID, relay.toBuf()));
        }
        if (!toDeliver.isEmpty()) save();
    }

    /** Look up a stored voice by id (on-demand fetch). */
    public synchronized StoredVoice findById(UUID voiceMessageId) {
        if (voiceMessageId == null) return null;
        for (StoredVoice v : undelivered) {
            if (v.voiceMessageId != null && v.voiceMessageId.equals(voiceMessageId)) {
                return v;
            }
        }
        return null;
    }

    private static String resolveRecipient(String senderUuid, String conversationId) {
        if (conversationId == null || !conversationId.contains(":")) return null;
        String[] parts = conversationId.split(":");
        if (parts.length != 2) return null;
        return parts[0].equals(senderUuid) ? parts[1] : parts[0];
    }

    private synchronized void cleanup() {
        long maxAgeMs = ModServerConfig.CONFIG.voiceOfflineMaxAgeHours.get() * 3600000L;
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        boolean changed = undelivered.removeIf(v -> v.timestamp < cutoff);
        if (changed) save();
    }

    private void startCleaner() {
        if (cleaner != null) cleaner.shutdown();
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatSphere-VoiceStore-Cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.MINUTES);
    }

    private void stopCleaner() {
        if (cleaner != null) { cleaner.shutdown(); cleaner = null; }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(storageDir);
            Path path = storageDir.resolve("index.json");
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            String json = GSON.toJson(undelivered);
            byte[] compressed = compress(json.getBytes(StandardCharsets.UTF_8));
            Files.write(tmp, compressed);
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("Failed to save voice storage", e);
        }
    }

    private synchronized void load() {
        try {
            Path path = storageDir.resolve("index.json");
            if (!Files.exists(path)) return;
            byte[] compressed = Files.readAllBytes(path);
            String json = new String(decompress(compressed), StandardCharsets.UTF_8);
            Type type = new TypeToken<List<StoredVoice>>(){}.getType();
            List<StoredVoice> loaded = GSON.fromJson(json, type);
            if (loaded != null) { undelivered.clear(); undelivered.addAll(loaded); }
        } catch (Exception e) {
            LOGGER.error("Failed to load voice storage", e);
        }
    }

    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(data); }
        return bos.toByteArray();
    }

    private static byte[] decompress(byte[] data) throws IOException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPInputStream gz = new GZIPInputStream(bis)) {
            byte[] buf = new byte[8192]; int r;
            while ((r = gz.read(buf)) >= 0) bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }
}
