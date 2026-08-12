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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSkinCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("PlayerSkinCache");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<UUID, PlayerSkin> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, CompletableFuture<Void>> PENDING_FETCHES = new ConcurrentHashMap<>();
    private static Path cacheDir;

    public static void setCacheDir(Path dir) {
        cacheDir = dir.resolve("skincache");
        loadCacheFile();
    }

    public static PlayerSkin getSkin(UUID uuid) {
        if (uuid == null) return DefaultPlayerSkin.get(uuid);

        PlayerSkin cached = CACHE.get(uuid);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                CACHE.put(uuid, info.getSkin());
                return info.getSkin();
            }
        }

        if (ModClientConfig.CONFIG.avatarCacheEnabled.get()) {
            String apiUrl = ModClientConfig.CONFIG.customSkinApiUrl.get();
            if (apiUrl != null && !apiUrl.isEmpty()) {
                fetchSkinAsync(uuid, apiUrl);
            }
        }

        return DefaultPlayerSkin.get(uuid);
    }

    private static void fetchSkinAsync(UUID uuid, String apiUrl) {
        if (PENDING_FETCHES.containsKey(uuid)) return;
        String uuidStr = uuid.toString().replace("-", "");
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                URL url = URI.create(apiUrl + "/sessionserver/session/minecraft/profile/" + uuidStr + "?unsigned=false").toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "ChatSphere/1.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() != 200) return;

                String json;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    json = sb.toString();
                }
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root == null || !root.has("properties")) return;
                JsonArray props = root.getAsJsonArray("properties");
                String texturesValue = null;
                for (JsonElement el : props) {
                    JsonObject prop = el.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        texturesValue = prop.get("value").getAsString();
                        break;
                    }
                }
                if (texturesValue == null) return;
                String decoded = new String(Base64.getDecoder().decode(texturesValue), StandardCharsets.UTF_8);
                JsonObject texturesJson = GSON.fromJson(decoded, JsonObject.class);
                if (texturesJson == null || !texturesJson.has("textures")) return;
                JsonObject textures = texturesJson.getAsJsonObject("textures");
                if (!textures.has("SKIN")) return;
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
                    } catch (Exception e) {
                        LOGGER.error("Skin texture registration failed for {}: {}", uuid, e.getMessage());
                    } finally {
                        PENDING_FETCHES.remove(uuid);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("Skin fetch failed for {}: {}", uuid, e.getMessage());
                PENDING_FETCHES.remove(uuid);
            }
        });
        PENDING_FETCHES.put(uuid, future);
    }

    public static void refreshCache() {
        List<UUID> uuids = new ArrayList<>(CACHE.keySet());
        CACHE.clear();
        PENDING_FETCHES.clear();
        String apiUrl = ModClientConfig.CONFIG.customSkinApiUrl.get();
        if (apiUrl == null || apiUrl.isEmpty()) return;
        for (UUID uuid : uuids) {
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
