package cn.sarskin.ChatSphere.client.image;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.platform.PlatformPaths;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Client side chat image cache: bytes on disk, textures created on first draw. */
public final class ChatImageCache {
    private static final int MAX_TEXTURES = 48;
    /** Side of a baked channel icon texture; large enough to stay sharp at 32 px with room to spare. */
    public static final int ICON_TEX = 128;
    /** Corner radius of a baked channel icon, as a fraction of the side. */
    public static final float ICON_CORNER = 0.25f;
    /** Samples per axis used to average corner coverage when baking an icon. */
    private static final int SS = 4;
    private static final Map<String, byte[]> BYTES = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, int[]> SIZES = new ConcurrentHashMap<>();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> LAST_USE = new LinkedHashMap<>();

    private ChatImageCache() {}

    public static Path dir() {
        return PlatformPaths.configDir().resolve("chatsphere").resolve("images");
    }

    public static boolean cached(String id) {
        return BYTES.containsKey(id) || diskFile(id) != null;
    }

    public static boolean requested(String id) {
        return REQUESTED.contains(id);
    }

    public static void markRequested(String id) {
        REQUESTED.add(id);
    }

    public static void markMissing(String id) {
        MISSING.add(id);
        REQUESTED.remove(id);
    }

    public static boolean missing(String id) {
        return MISSING.contains(id);
    }

    public static void put(String id, byte[] data) {
        if (id == null || data == null || data.length == 0) return;
        BYTES.put(id, data);
        try {
            Files.createDirectories(dir());
            Files.write(dir().resolve(id + ".img"), data);
            trimToLimit();
        } catch (Exception ignored) {
        }
    }

    /** Disk budget from the client config; oldest files go first. */
    private static void trimToLimit() {
        long limit = Math.max(1, ModClientConfig.CONFIG.chatImageCacheMb.get()) * 1024L * 1024L;
        List<Path> files = new ArrayList<>();
        long total = 0;
        try (Stream<Path> stream = Files.list(dir())) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".img")) continue;
                files.add(path);
                total += Files.size(path);
            }
        } catch (Exception e) {
            return;
        }
        if (total <= limit) return;
        files.sort(Comparator.comparingLong(ChatImageCache::lastModified));
        for (Path path : files) {
            if (total <= limit) break;
            try {
                long size = Files.size(path);
                if (Files.deleteIfExists(path)) {
                    total -= size;
                    String name = path.getFileName().toString();
                    BYTES.remove(name.substring(0, name.length() - 4));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static byte[] bytes(String id) {
        byte[] data = BYTES.get(id);
        if (data != null) return data;
        try {
            Path file = dir().resolve(id + ".img");
            if (!Files.isRegularFile(file)) return null;
            if (expired(file)) {
                Files.deleteIfExists(file);
                return null;
            }
            data = Files.readAllBytes(file);
            BYTES.put(id, data);
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private static Path diskFile(String id) {
        Path file = dir().resolve(id + ".img");
        try {
            if (!Files.isRegularFile(file) || expired(file)) return null;
            return file;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean expired(Path file) throws Exception {
        long ttl = Math.max(1, ModServerConfig.CONFIG.chatImageClientTtlMinutes.get()) * 60_000L;
        return System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis() > ttl;
    }

    /** MC decodes png only, so jpeg goes through ImageIO first; null when nothing here can read it. */
    private static byte[] toPng(byte[] data) {
        if (ChatImageGuard.isPng(data)) return data;
        if (data == null || data.length == 0) return null;
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(image, "png", out)) return null;
            byte[] png = out.toByteArray();
            return png.length > 0 ? png : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** Full eight byte PNG signature; the four byte check alone lets a corrupt entry through. */
    private static boolean strictPng(byte[] d) {
        return d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 0x50 && d[2] == 0x4E && d[3] == 0x47
                && (d[4] & 0xFF) == 0x0D && (d[5] & 0xFF) == 0x0A && (d[6] & 0xFF) == 0x1A && (d[7] & 0xFF) == 0x0A;
    }



    /** Texture for a cached image, or null while the bytes are missing; built on the render thread. */
    public static ResourceLocation texture(String id) {
        ResourceLocation cached = TEXTURES.get(id);
        if (cached != null) {
            LAST_USE.put(id, System.currentTimeMillis());
            return cached;
        }
        byte[] data = bytes(id);
        if (data != null && ChatImageGuard.isPng(data) && !strictPng(data)) {
            BYTES.remove(id);
            try {
                Files.deleteIfExists(dir().resolve(id + ".img"));
            } catch (Exception ignored) {
            }
            data = null;
        }
        if (data == null) {
            return null;
        }
        if (ChatImageGuard.extensionFor(data) == null) {
            return null;
        }
        byte[] png = ChatImageGuard.pngBytes(data);
        if (png == null) {
            return null;
        }
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            SIZES.put(id, new int[]{image.getWidth(), image.getHeight()});
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("chatsphere", "images/" + id);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            TEXTURES.put(id, location);
            LAST_USE.put(id, System.currentTimeMillis());
            evict();
            return location;
        } catch (Exception e) {
            return null;
        }
    }

    public static int[] size(String id) {
        int[] size = SIZES.get(id);
        if (size != null) return size;
        byte[] data = BYTES.get(id);
        if (data == null) return null;
        int[] out = new int[2];
        return ChatImageGuard.validate(data, out) == null || ChatImageGuard.extensionFor(data) != null ? out : null;
    }

    /** Pixel size of a texture that was already built, else null. */
    public static int[] textureSize(String id) {
        return SIZES.get(id);
    }

    /** Corner baked into the alpha: a draw-time mask is only as fine as one logical pixel, a staircase at GUI scale 3+. */
    public static ResourceLocation iconTexture(String id) {
        String key = id + ":icon";
        ResourceLocation cached = TEXTURES.get(key);
        if (cached != null) {
            LAST_USE.put(key, System.currentTimeMillis());
            return cached;
        }
        byte[] data = bytes(id);
        if (data == null) return null;
        byte[] png = ChatImageGuard.pngBytes(data);
        if (png == null) return null;
        byte[] baked = roundSquare(png, ICON_TEX, Math.round(ICON_TEX * ICON_CORNER));
        if (baked == null) return null;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(baked));
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("chatsphere", "images/" + id + "_icon");
            Minecraft.getInstance().getTextureManager().register(location, texture);
            TEXTURES.put(key, location);
            LAST_USE.put(key, System.currentTimeMillis());
            evict();
            return location;
        } catch (Exception e) {
            return null;
        }
    }

    /** Centre-crops to a square and masks a rounded corner in, both anti-aliased. */
    private static byte[] roundSquare(byte[] png, int side, int radius) {
        try {
            java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new ByteArrayInputStream(png));
            if (src == null || src.getWidth() <= 0 || src.getHeight() <= 0) return null;
            java.awt.image.BufferedImage scaled =
                    new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int edge = Math.min(src.getWidth(), src.getHeight());
                int sx = (src.getWidth() - edge) / 2;
                int sy = (src.getHeight() - edge) / 2;
                g.drawImage(src, 0, 0, side, side, sx, sy, sx + edge, sy + edge, null);
            } finally {
                g.dispose();
            }
            // Supersampled by hand: Java2D's DstIn leaves the alpha at 255 on a plain ARGB image, so it would not cut out
            double r = radius;
            int[] px = new int[side * side];
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int inside = 0;
                    for (int oy = 0; oy < SS; oy++) {
                        for (int ox = 0; ox < SS; ox++) {
                            if (insideRounded(x + (ox + 0.5) / SS, y + (oy + 0.5) / SS, side, r)) inside++;
                        }
                    }
                    int argb = scaled.getRGB(x, y);
                    int a = (argb >>> 24) * Math.round(255f * inside / (SS * SS)) / 255;
                    px[y * side + x] = (a << 24) | (argb & 0x00FFFFFF);
                }
            }
            java.awt.image.BufferedImage out =
                    new java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, side, side, px, 0, side);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            if (!javax.imageio.ImageIO.write(out, "png", bytes)) return null;
            return bytes.toByteArray();
        } catch (Throwable e) {
            return null;
        }
    }

    /** True when the point lies inside the rounded square; the flat spans are unbounded in one axis. */
    private static boolean insideRounded(double x, double y, int side, double r) {
        double cx = Math.min(Math.max(x, r), side - r);
        double cy = Math.min(Math.max(y, r), side - r);
        double dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static void evict() {
        while (TEXTURES.size() > MAX_TEXTURES) {
            String oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<String, Long> entry : LAST_USE.entrySet()) {
                if (entry.getValue() < oldestAt) {
                    oldestAt = entry.getValue();
                    oldest = entry.getKey();
                }
            }
            if (oldest == null) return;
            ResourceLocation location = TEXTURES.remove(oldest);
            LAST_USE.remove(oldest);
            if (location != null) {
                try {
                    Minecraft.getInstance().getTextureManager().release(location);
                } catch (Exception ignored) {
                }
            }
        }
    }
}
