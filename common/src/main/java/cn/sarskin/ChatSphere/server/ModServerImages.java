package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.client.image.ChatImageGuard;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.storage.ModStoragePaths;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Server side chat image store: validated bytes on disk under a client supplied hex id, served on
 * request and swept once the configured lifetime is over.
 */
public final class ModServerImages {
    public static final int CHUNK_BYTES = 16 * 1024;
    public static final int MAX_CHUNKS = 48;
    private static final Map<MinecraftServer, ModServerImages> INSTANCES = new ConcurrentHashMap<>();

    private final Path dir;
    private final Map<UUID, Long> lastUploadAt = new HashMap<>();
    private final Map<UUID, Integer> uploadCounts = new HashMap<>();

    private ModServerImages(Path dir) {
        this.dir = dir;
    }

    public static synchronized ModServerImages getInstance(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> new ModServerImages(
                ModStoragePaths.getServerDataDir().resolve("images")));
    }

    public static synchronized void removeServer(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    /** Accepts one upload; returns null plus the stored size when it was written. */
    public synchronized String store(String id, byte[] data, String sourceUrl, int[] dimsOut) {
        if (!validId(id)) return ChatImageGuard.ERR_FORMAT;
        String error = ChatImageGuard.validate(data, dimsOut);
        if (error != null) return error;
        try {
            Files.createDirectories(dir);
            String ext = ChatImageGuard.extensionFor(data);
            if (ext == null) return ChatImageGuard.ERR_FORMAT;
            Path target = dir.resolve(id + "." + ext);
            Files.write(target, data);
            Files.writeString(dir.resolve(id + ".meta"), sourceUrl == null ? "" : sourceUrl);
            return null;
        } catch (Exception e) {
            return ChatImageGuard.ERR_FORMAT;
        }
    }

    public synchronized byte[] load(String id) {
        if (!validId(id)) return null;
        for (String ext : new String[]{"png", "jpg"}) {
            Path file = dir.resolve(id + "." + ext);
            try {
                if (Files.isRegularFile(file)) return Files.readAllBytes(file);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public synchronized long uploadCooldownRemaining(UUID uuid) {
        int seconds = Math.max(0, ModServerConfig.CONFIG.chatImageUploadCooldownSeconds.get());
        if (seconds <= 0) return 0;
        Long last = lastUploadAt.get(uuid);
        if (last == null) return 0;
        long left = seconds * 1000L - (System.currentTimeMillis() - last);
        return Math.max(0, left);
    }

    public synchronized void recordUpload(UUID uuid) {
        if (uuid != null) {
            lastUploadAt.put(uuid, System.currentTimeMillis());
            uploadCounts.merge(uuid, 1, Integer::sum);
        }
        if (lastUploadAt.size() > 512) {
            long cutoff = System.currentTimeMillis() - 3_600_000L;
            lastUploadAt.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }

    /** Images this player uploaded since the server started. */
    public synchronized int countFor(UUID uuid) {
        return uuid == null ? 0 : uploadCounts.getOrDefault(uuid, 0);
    }

    public synchronized int count() {
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> stream = Files.list(dir)) {
            return (int) stream.filter(path -> path.getFileName().toString().endsWith(".meta")).count();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Drops files past the server lifetime; called on an interval from the server tick hooks. */
    public synchronized void sweep() {
        if (!Files.isDirectory(dir)) return;
        long ttl = Math.max(1, ModServerConfig.CONFIG.chatImageServerTtlMinutes.get()) * 60_000L;
        long now = System.currentTimeMillis();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                try {
                    if (now - Files.getLastModifiedTime(path).toMillis() > ttl) Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean validId(String id) {
        if (id == null || id.length() != 16) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return false;
        }
        return true;
    }
}
