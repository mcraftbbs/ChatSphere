package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.storage.ModStoragePaths;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ModVoiceCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-VoiceCache");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, CachedVoice> cache = new ConcurrentHashMap<>();
    private static Path cacheDir;
    private static ScheduledExecutorService cleaner;
    private static boolean loaded;

    public record CachedVoice(
            String conversationId,
            String conversationType,
            UUID senderUuid,
            long timestamp,
            int frameCount,
            byte[] audioData
    ) {}

    public static void init() {
        if (loaded) return;
        loaded = true;
        // Detect server context
        Minecraft mc = Minecraft.getInstance();
        cacheDir = ModStoragePaths.getClientBaseDir().resolve("voice_cache");
        load();
        startCleaner();
    }

    public static void save(String conversationId, String conversationType,
                             UUID senderUuid, UUID playbackUuid,
                             byte[] audioData, int frameCount) {
        if (!ModClientConfig.CONFIG.voiceCacheEnabled.get()) return;
        init();
        cache.put(playbackUuid, new CachedVoice(
                conversationId, conversationType, senderUuid,
                System.currentTimeMillis(), frameCount, audioData));
        enforceSizeLimit();
        saveIndex();
    }

    public static CachedVoice get(UUID playbackUuid) {
        if (!ModClientConfig.CONFIG.voiceCacheEnabled.get()) return null;
        init();
        return cache.get(playbackUuid);
    }

    public static byte[] getAudioData(UUID playbackUuid) {
        CachedVoice cv = get(playbackUuid);
        return cv != null ? cv.audioData : null;
    }

    public static Integer getFrameCount(UUID playbackUuid) {
        CachedVoice cv = get(playbackUuid);
        return cv != null ? cv.frameCount : null;
    }

    private static void cleanup() {
        if (!ModClientConfig.CONFIG.voiceCacheEnabled.get()) return;
        long maxAgeMs = ModClientConfig.CONFIG.voiceCacheMaxAgeHours.get() * 3600000L;
        long cutoff = System.currentTimeMillis() - maxAgeMs;
        boolean changed = cache.entrySet().removeIf(e -> e.getValue().timestamp < cutoff);
        if (changed) saveIndex();
    }

    private static void enforceSizeLimit() {
        long maxBytes = ModClientConfig.CONFIG.voiceCacheMaxMB.get() * 1024L * 1024;
        long total = 0;
        for (CachedVoice cv : cache.values()) total += cv.audioData().length;
        if (total <= maxBytes) return;
        List<Map.Entry<UUID, CachedVoice>> sorted = new ArrayList<>(cache.entrySet());
        sorted.sort(Map.Entry.comparingByValue((a, b) -> Long.compare(a.timestamp(), b.timestamp())));
        for (Map.Entry<UUID, CachedVoice> e : sorted) {
            if (total <= maxBytes) break;
            total -= e.getValue().audioData().length;
            cache.remove(e.getKey());
        }
        LOGGER.info("Voice cache exceeded {} MB, evicted oldest entries", ModClientConfig.CONFIG.voiceCacheMaxMB.get());
    }

    private static void startCleaner() {
        if (cleaner != null) cleaner.shutdown();
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChatSphere-VoiceCache-Cleaner");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(ModVoiceCache::cleanup, 15, 15, TimeUnit.MINUTES);
    }

    // Persistence
    private static Path getIndexPath() {
        return cacheDir.resolve("index.json");
    }

    private static synchronized void saveIndex() {
        try {
            Files.createDirectories(cacheDir);
            Map<String, CachedVoice> serializable = new LinkedHashMap<>();
            for (Map.Entry<UUID, CachedVoice> e : cache.entrySet()) {
                serializable.put(e.getKey().toString(), e.getValue());
            }
            String json = GSON.toJson(serializable);
            Path path = getIndexPath();
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("Failed to save voice cache", e);
        }
    }

    private static synchronized void load() {
        try {
            Path path = getIndexPath();
            if (!Files.exists(path)) return;
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, CachedVoice>>(){}.getType();
            Map<String, CachedVoice> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                cache.clear();
                for (Map.Entry<String, CachedVoice> e : loaded.entrySet()) {
                    cache.put(UUID.fromString(e.getKey()), e.getValue());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load voice cache", e);
        }
    }
}
