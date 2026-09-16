package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSkinCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerSkinCache");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, PlayerSkin> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CompletableFuture<Void>> PENDING_FETCHES = new ConcurrentHashMap<>();
    /** API answered without a usable profile; not retried until a manual refresh. */
    private static final Set<UUID> UNKNOWN = ConcurrentHashMap.newKeySet();
    /** Transient failures back off instead of retrying every frame. */
    private static final Map<UUID, Failure> FAILURES = new ConcurrentHashMap<>();
    private static final long RETRY_BASE_MS = 30_000L;
    private static volatile int unknownCount;
    private static volatile boolean apiLooksWrongLogged;
    private static Path cacheDir;

    private record Failure(long at, int attempts) {}

    public static void setCacheDir(Path dir) {
        cacheDir = dir.resolve("skincache");
        loadCacheFile();
    }

    public static PlayerSkin getSkin(UUID uuid) {
        if (uuid == null) return DefaultPlayerSkin.get(uuid);

        PlayerSkin cached = CACHE.get(uuid);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(uuid) : null;
        PlayerSkin online = info != null ? info.getSkin() : null;

        String apiUrl = skinApiUrl();
        if (apiUrl == null) {
            // No API configured: the tab list skin is all there is
            if (online != null) {
                CACHE.put(uuid, online);
                return online;
            }
            return DefaultPlayerSkin.get(uuid);
        }
        // Don't cache the fallback while an API is set: it would win over the fetched texture
        fetchSkinAsync(uuid, apiUrl);
        return online != null ? online : DefaultPlayerSkin.get(uuid);
    }

    private static String skinApiUrl() {
        if (!ModClientConfig.CONFIG.avatarCacheEnabled.get()) return null;
        String url = ModClientConfig.CONFIG.customSkinApiUrl.get();
        if (url == null || url.isBlank()) return null;
        return url.trim();
    }

    /** Endpoints to try: {uuid} as given, a profile endpoint gets the id, else an Yggdrasil base. */
    private static List<String> profileUrls(String apiUrl, UUID uuid) {
        String base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        String plain = uuid.toString().replace("-", "");
        List<String> out = new ArrayList<>();
        if (base.contains("{uuid}")) {
            out.add(base.replace("{uuid}", plain));
            out.add(base.replace("{uuid}", uuid.toString()));
            return out;
        }
        if (base.contains("/sessionserver/")) {
            out.add(base + "/" + plain + "?unsigned=false");
            return out;
        }
        out.add(base + "/sessionserver/session/minecraft/profile/" + plain + "?unsigned=false");
        out.add(base + "/api/yggdrasil/sessionserver/session/minecraft/profile/" + plain + "?unsigned=false");
        out.add(base + "/api/sessionserver/session/minecraft/profile/" + plain + "?unsigned=false");
        return out;
    }

    private record HttpResult(int status, String body) {
        boolean ok() {
            return status == 200 && body != null;
        }

        /** 4xx means the skin server has no such profile. */
        boolean unknownProfile() {
            return status >= 400 && status < 500;
        }
    }

    private static HttpResult httpGet(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "ChatSphere/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int status = conn.getResponseCode();
            if (status != 200) {
                LOGGER.debug("Skin API {} -> HTTP {}", url, status);
                return new HttpResult(status, null);
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return new HttpResult(200, sb.toString());
            }
        } catch (Exception e) {
            LOGGER.debug("Skin API {} failed: {}", url, e.getMessage());
            return new HttpResult(0, null);
        }
    }

    private static void fetchSkinAsync(UUID uuid, String apiUrl) {
        if (PENDING_FETCHES.containsKey(uuid) || UNKNOWN.contains(uuid)) return;
        Failure failure = FAILURES.get(uuid);
        if (failure != null) {
            long wait = RETRY_BASE_MS << Math.min(failure.attempts() - 1, 4);
            if (System.currentTimeMillis() - failure.at() < wait) return;
        }
        String uuidStr = uuid.toString().replace("-", "");
        List<String> candidates = profileUrls(apiUrl, uuid);
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                String json = null;
                boolean unknownProfile = false;
                for (String candidate : candidates) {
                    HttpResult result = httpGet(candidate);
                    if (result.ok()) {
                        json = result.body();
                        break;
                    }
                    if (result.unknownProfile()) unknownProfile = true;
                }
                if (json == null) {
                    PENDING_FETCHES.remove(uuid);
                    if (unknownProfile) {
                        // Chat can mention players from other servers; stop asking and stay quiet
                        markUnknown(uuid, uuidStr, apiUrl);
                    } else {
                        failFetch(uuid, uuidStr, "no response");
                    }
                    return;
                }
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root == null || !root.has("properties")) {
                    markUnknown(uuid, uuidStr, apiUrl);
                    return;
                }
                JsonArray props = root.getAsJsonArray("properties");
                String texturesValue = null;
                for (JsonElement el : props) {
                    JsonObject prop = el.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        texturesValue = prop.get("value").getAsString();
                        break;
                    }
                }
                if (texturesValue == null) {
                    markUnknown(uuid, uuidStr, apiUrl);
                    return;
                }
                String decoded = new String(Base64.getDecoder().decode(texturesValue), StandardCharsets.UTF_8);
                JsonObject texturesJson = GSON.fromJson(decoded, JsonObject.class);
                if (texturesJson == null || !texturesJson.has("textures")) {
                    markUnknown(uuid, uuidStr, apiUrl);
                    return;
                }
                JsonObject textures = texturesJson.getAsJsonObject("textures");
                if (!textures.has("SKIN")) {
                    markUnknown(uuid, uuidStr, apiUrl);
                    return;
                }
                JsonObject skinObj = textures.getAsJsonObject("SKIN");
                String skinUrl = skinObj.get("url").getAsString();
                String model = "default";
                if (skinObj.has("metadata")) {
                    JsonObject meta = skinObj.getAsJsonObject("metadata");
                    if (meta.has("model")) model = meta.get("model").getAsString();
                }
                String capeUrl = textures.has("CAPE") ? textures.getAsJsonObject("CAPE").get("url").getAsString() : null;

                NativeImage skinImg;
                try (InputStream in = URI.create(skinUrl).toURL().openStream()) {
                    skinImg = NativeImage.read(in);
                }
                NativeImage capeImg = null;
                if (capeUrl != null) {
                    try (InputStream in = URI.create(capeUrl).toURL().openStream()) {
                        capeImg = NativeImage.read(in);
                    }
                }

                final NativeImage finalSkin = skinImg;
                final NativeImage finalCape = capeImg;
                final String finalModel = model;
                Minecraft.getInstance().execute(() -> {
                    try {
                        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("chatsphere", "skins/" + uuidStr);
                        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(finalSkin));
                        ResourceLocation capeRes = null;
                        if (finalCape != null) {
                            capeRes = ResourceLocation.fromNamespaceAndPath("chatsphere", "capes/" + uuidStr);
                            Minecraft.getInstance().getTextureManager().register(capeRes, new DynamicTexture(finalCape));
                        }
                        PlayerSkin.Model skinModel = "slim".equals(finalModel) ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
                        PlayerSkin skin = new PlayerSkin(loc, "slim".equals(finalModel) ? "slim" : null, capeRes, capeRes, skinModel, true);
                        CACHE.put(uuid, skin);
                        UNKNOWN.remove(uuid);
                        FAILURES.remove(uuid);
                    } catch (Exception e) {
                        LOGGER.error("Skin texture registration failed for {}: {}", uuid, e.getMessage());
                        FAILURES.put(uuid, new Failure(System.currentTimeMillis(), 1));
                    } finally {
                        PENDING_FETCHES.remove(uuid);
                    }
                });
            } catch (Exception e) {
                failFetch(uuid, uuidStr, e.getMessage());
            }
        });
        PENDING_FETCHES.put(uuid, future);
    }

    /** No such profile on the API: remember it and stop asking. */
    private static void markUnknown(UUID uuid, String uuidStr, String apiUrl) {
        UNKNOWN.add(uuid);
        FAILURES.remove(uuid);
        PENDING_FETCHES.remove(uuid);
        LOGGER.debug("Skin API has no profile for {}", uuidStr);
        // A wrong URL 404s every uuid, which would otherwise look like unknown players
        if (++unknownCount >= 3 && !apiLooksWrongLogged) {
            apiLooksWrongLogged = true;
            LOGGER.warn("Skin API {} returned no profile for {} players, check customSkinApiUrl", apiUrl, unknownCount);
        }
    }

    /** Transient failure: free the slot and back off. */
    private static void failFetch(UUID uuid, String uuidStr, String detail) {
        Failure previous = FAILURES.get(uuid);
        int attempts = previous == null ? 1 : previous.attempts() + 1;
        FAILURES.put(uuid, new Failure(System.currentTimeMillis(), attempts));
        PENDING_FETCHES.remove(uuid);
        LOGGER.debug("Skin fetch failed for {} (attempt {}): {}", uuidStr, attempts, detail);
    }

    public static void refreshCache() {
        // Refetch every known player, not just the cached ones: the point is to replace default avatars
        Set<UUID> targets = new LinkedHashSet<>(CACHE.keySet());
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile() != null && info.getProfile().getId() != null) {
                    targets.add(info.getProfile().getId());
                }
            }
        }
        if (mc.player != null) targets.add(mc.player.getUUID());
        CACHE.clear();
        PENDING_FETCHES.clear();
        UNKNOWN.clear();
        FAILURES.clear();
        unknownCount = 0;
        apiLooksWrongLogged = false;
        String apiUrl = skinApiUrl();
        if (apiUrl == null) return;
        for (UUID uuid : targets) {
            fetchSkinAsync(uuid, apiUrl);
        }
    }

    private static Path getCacheFile() {
        if (cacheDir == null) cacheDir = Path.of("").resolve("chatsphere_skincache");
        try { Files.createDirectories(cacheDir); } catch (Exception ignored) {}
        return cacheDir.resolve("CACHE.json");
    }

    private static void loadCacheFile() {
        Path file = getCacheFile();
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("Failed to load skin CACHE", e);
        }
    }
}
