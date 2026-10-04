package cn.sarskin.ChatSphere.client.link;

import cn.sarskin.ChatSphere.config.ModClientConfig;import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Disk + texture cache for link cards; thumbnails only become textures when they are drawn. */
public final class LinkPreviewCache {
    private static final int MAX_ENTRIES = 128;
    private static final Map<String, LinkPreview> MEMORY =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, LinkPreview> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };
    private static final Map<String, byte[]> THUMBS = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();

    private LinkPreviewCache() {}

    private static Path dir() {
        return cn.sarskin.ChatSphere.platform.PlatformPaths.configDir()
                .resolve("chatsphere").resolve("links");
    }

    static String key(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(url.hashCode());
        }
    }

    public static LinkPreview get(String url) {
        LinkPreview hit = MEMORY.get(url);
        if (hit != null) return hit;
        return readDisk(url);
    }

    public static boolean known(String url) {
        return MEMORY.containsKey(url) || FAILED.containsKey(url);
    }

    public static boolean failedRecently(String url) {
        Long at = FAILED.get(url);
        if (at == null) return false;
        if (System.currentTimeMillis() - at > 10L * 60L * 1000L) {
            FAILED.remove(url);
            return false;
        }
        return true;
    }

    static void markFailed(String url) {
        FAILED.put(url, System.currentTimeMillis());
    }

    public static void put(String url, LinkPreview preview, byte[] thumb) {
        MEMORY.put(url, preview);
        if (thumb != null && thumb.length > 0) THUMBS.put(url, thumb);
        writeDisk(url, preview, thumb);
    }

    /** Registers the thumbnail texture on first draw; null while the bytes are missing or invalid. */
    public static ResourceLocation texture(String url) {
        ResourceLocation cached = TEXTURES.get(url);
        if (cached != null) return cached;
        byte[] data = THUMBS.get(url);
        if (data == null) {
            data = readThumb(url);
            if (data == null) return null;
            THUMBS.put(url, data);
        }
        if (!isImage(data)) return null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = new ResourceLocation("chatsphere", "links/" + key(url));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TEXTURES.put(url, id);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation id : TEXTURES.values()) {
            try {
                mc.getTextureManager().release(id);
            } catch (Exception ignored) {
            }
        }
        TEXTURES.clear();
        THUMBS.clear();
        MEMORY.clear();
        FAILED.clear();
    }

    /** Drops expired disk entries; cheap enough to run on join. */
    public static void prune() {
        long ttl = ttlMs();
        Path dir = dir();
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                try {
                    if (System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis() > ttl) {
                        Files.deleteIfExists(path);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static long ttlMs() {
        return Math.max(1, ModClientConfig.CONFIG.linkPreviewTtlHours.get()) * 60L * 60L * 1000L;
    }

    private static LinkPreview readDisk(String url) {
        Path meta = dir().resolve(key(url) + ".json");
        try {
            if (!Files.isRegularFile(meta)) return null;
            if (System.currentTimeMillis() - Files.getLastModifiedTime(meta).toMillis() > ttlMs()) {
                Files.deleteIfExists(meta);
                return null;
            }
            JsonObject json = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
            LinkPreview preview = new LinkPreview(url, str(json, "title"), str(json, "description"),
                    str(json, "imageUrl"), str(json, "site"));
            MEMORY.put(url, preview);
            byte[] thumb = readThumb(url);
            if (thumb != null) THUMBS.put(url, thumb);
            return preview;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readThumb(String url) {
        Path image = dir().resolve(key(url) + ".thumb");
        try {
            if (!Files.isRegularFile(image)) return null;
            if (System.currentTimeMillis() - Files.getLastModifiedTime(image).toMillis() > ttlMs()) return null;
            return Files.readAllBytes(image);
        } catch (Exception e) {
            return null;
        }
    }

    /** PNG or JPEG magic; thumbnails keep the fetched bytes so the extension cannot be trusted. */
    private static boolean isImage(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return true;
        return d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    private static void writeDisk(String url, LinkPreview preview, byte[] thumb) {
        Path dir = dir();
        try {
            Files.createDirectories(dir);
            JsonObject json = new JsonObject();
            json.addProperty("title", preview.title());
            json.addProperty("description", preview.description());
            json.addProperty("imageUrl", preview.imageUrl());
            json.addProperty("site", preview.site());
            Files.writeString(dir.resolve(key(url) + ".json"), json.toString(), StandardCharsets.UTF_8);
            if (thumb != null && thumb.length > 0) {
                Files.write(dir.resolve(key(url) + ".thumb"), thumb);
            }
        } catch (Exception ignored) {
        }
    }

    private static String str(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
}
