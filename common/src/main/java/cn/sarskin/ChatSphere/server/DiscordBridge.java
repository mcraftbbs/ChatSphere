package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.StoredMessage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Mirrors one ChatSphere channel to a Discord channel and can pull Discord messages back.
 * Outbound prefers a webhook URL and falls back to a bot token posting to a channel id.
 * Only CHANNEL traffic is mirrored: private chats and the command console stay on the server.
 */
public class DiscordBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-Discord");
    private static final String API_BASE = "https://discord.com/api/v10";
    /** Discord hard limit is 2000 characters; leave room for the sender prefix. */
    private static final int MAX_CONTENT = 1800;
    private static final long ERROR_LOG_INTERVAL_MS = 60_000L;
    private static final int INBOUND_LIMIT = 50;

    private static final Map<MinecraftServer, DiscordBridge> INSTANCES = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final MinecraftServer server;
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ChatSphere-Discord");
        t.setDaemon(true);
        return t;
    });

    /** Newest Discord message id already handled; inbound polling resumes after it. */
    private volatile String cursor;
    private volatile boolean cursorReady;
    private volatile long nextPollAt;
    private volatile long outboundBlockedUntil;
    private volatile long lastErrorAt;
    private volatile String lastError = "";

    private DiscordBridge(MinecraftServer server) {
        this.server = server;
    }

    public static DiscordBridge getInstance(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> {
            DiscordBridge bridge = new DiscordBridge(s);
            bridge.poller.scheduleWithFixedDelay(bridge::tick, 2, 1, TimeUnit.SECONDS);
            return bridge;
        });
    }

    public static void removeServer(MinecraftServer server) {
        DiscordBridge bridge = INSTANCES.remove(server);
        if (bridge != null) bridge.poller.shutdownNow();
    }

    /** Last error text for the doctor command; empty when nothing failed recently. */
    public String lastError() {
        return lastError;
    }

    public boolean webhookConfigured() {
        return !ModServerConfig.CONFIG.discordWebhookUrl.get().isEmpty();
    }

    public boolean botConfigured() {
        return !ModServerConfig.CONFIG.discordBotToken.get().isEmpty()
                && !ModServerConfig.CONFIG.discordChannelId.get().isEmpty();
    }

    public String mirrorChannel() {
        String id = ModServerConfig.CONFIG.discordMirrorChannel.get();
        return id == null ? "" : id.trim();
    }

    /** Called on the server thread for every stored message. */
    public void onChannelMessage(StoredMessage msg) {
        if (msg == null || !"CHANNEL".equals(msg.conversationType())) return;
        if (!ModServerConfig.CONFIG.discordEnabled.get()) return;
        String mirror = mirrorChannel();
        if (mirror.isEmpty() || !mirror.equals(msg.conversationId())) return;
        if (System.currentTimeMillis() < outboundBlockedUntil) return;

        String text = formatOutbound(msg);
        if (text.isEmpty()) return;
        String sender = sanitizeName(msg.senderName());

        HttpRequest request = null;
        String webhook = ModServerConfig.CONFIG.discordWebhookUrl.get();
        String token = ModServerConfig.CONFIG.discordBotToken.get();
        String channelId = ModServerConfig.CONFIG.discordChannelId.get();
        try {
            if (webhook != null && !webhook.isEmpty()) {
                request = jsonRequest(webhook, null)
                        .POST(HttpRequest.BodyPublishers.ofString(payload(sender, text, true)))
                        .build();
            } else if (token != null && !token.isEmpty() && channelId != null && !channelId.isEmpty()) {
                request = jsonRequest(API_BASE + "/channels/" + channelId + "/messages", "Bot " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(payload(sender, text, false)))
                        .build();
            }
        } catch (IllegalArgumentException e) {
            noteError("webhook url is not a valid URL");
            return;
        }
        if (request == null) return;

        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    int code = resp.statusCode();
                    if (code == 429) {
                        applyRetryAfter(resp.body());
                    } else if (code >= 300) {
                        noteError("send HTTP " + code + hint(code));
                    }
                })
                .exceptionally(e -> {
                    noteError("send failed: " + rootMessage(e));
                    return null;
                });
    }

    private void tick() {
        try {
            if (!ModServerConfig.CONFIG.discordEnabled.get()) {
                cursorReady = false;
                return;
            }
            if (!ModServerConfig.CONFIG.discordRelayInbound.get()) return;
            if (!botConfigured()) return;
            long now = System.currentTimeMillis();
            if (now < nextPollAt) return;
            int seconds = Math.max(2, Math.min(600, ModServerConfig.CONFIG.discordPollSeconds.get()));
            nextPollAt = now + seconds * 1000L;
            pollInbound();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            noteError("poll failed: " + rootMessage(e));
        }
    }

    private void pollInbound() throws Exception {
        String token = ModServerConfig.CONFIG.discordBotToken.get();
        String channelId = ModServerConfig.CONFIG.discordChannelId.get();
        if (!cursorReady) {
            // First pass only records where the channel is now: no backlog replay
            cursor = latestMessageId(token, channelId);
            cursorReady = true;
            return;
        }
        String url = API_BASE + "/channels/" + channelId + "/messages?limit=" + INBOUND_LIMIT;
        if (cursor != null && !cursor.isEmpty()) url += "&after=" + cursor;
        HttpResponse<String> resp = HTTP.send(
                jsonRequest(url, "Bot " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        int code = resp.statusCode();
        if (code == 429) {
            applyRetryAfter(resp.body());
            return;
        }
        if (code != 200) {
            noteError("poll HTTP " + code + hint(code));
            return;
        }
        JsonElement parsed = JsonParser.parseString(resp.body());
        if (!parsed.isJsonArray()) return;
        JsonArray array = parsed.getAsJsonArray();
        List<JsonObject> ordered = new ArrayList<>();
        for (JsonElement e : array) {
            if (e.isJsonObject()) ordered.add(e.getAsJsonObject());
        }
        ordered.sort(Comparator.comparingLong(DiscordBridge::snowflake));
        for (JsonObject m : ordered) {
            String id = str(m, "id");
            if (id == null) continue;
            if (cursor != null && snowflake(m) <= parseSnowflake(cursor)) continue;
            cursor = id;
            // Our own posts come back as webhook or bot messages; skipping them breaks the echo loop
            if (m.has("webhook_id")) continue;
            JsonObject author = m.getAsJsonObject("author");
            if (author == null) continue;
            if (author.has("bot") && author.get("bot").getAsBoolean()) continue;
            String content = str(m, "content");
            if (content == null || content.isBlank()) continue;
            String name = str(author, "username");
            relayInbound(name == null || name.isEmpty() ? "Discord" : name, content);
        }
    }

    private String latestMessageId(String token, String channelId) throws Exception {
        HttpResponse<String> resp = HTTP.send(
                jsonRequest(API_BASE + "/channels/" + channelId + "/messages?limit=1", "Bot " + token).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 429) {
            applyRetryAfter(resp.body());
            return null;
        }
        if (resp.statusCode() != 200) {
            noteError("poll HTTP " + resp.statusCode() + hint(resp.statusCode()));
            return null;
        }
        JsonElement parsed = JsonParser.parseString(resp.body());
        if (!parsed.isJsonArray()) return null;
        JsonArray array = parsed.getAsJsonArray();
        if (array.size() == 0) return null;
        return str(array.get(0).getAsJsonObject(), "id");
    }

    private void relayInbound(String author, String content) {
        String mirror = mirrorChannel();
        if (mirror.isEmpty()) return;
        String name = sanitizeName(author);
        String text = truncate(content.replace("\r", "").trim(), MAX_CONTENT);
        server.execute(() -> {
            ModServerChannels msc = ModServerChannels.getInstance(server);
            String target = msc.resolveChatChannel(mirror);
            if (target == null) {
                noteError("mirror channel " + mirror + " is not a chat channel on this server");
                return;
            }
            StoredMessage stored = msc.addChatMessage(name, Util.NIL_UUID, "[Discord] " + text,
                    target, "CHANNEL", "", "", "", false, false);
            msc.relayToOnlineMembers(stored);
        });
    }

    private static String formatOutbound(StoredMessage msg) {
        String content = msg.content() == null ? "" : msg.content();
        if (content.startsWith("VoiceMessage#")) {
            content = "[voice message]";
        }
        if (msg.itemNbt() != null && !msg.itemNbt().isEmpty()) {
            content = content.isEmpty() ? "[item]" : content + " [item]";
        }
        return truncate(content.replace("\r", "").trim(), MAX_CONTENT);
    }

    private static String payload(String sender, String text, boolean webhook) {
        JsonObject json = new JsonObject();
        if (webhook) {
            json.addProperty("username", sender);
            json.addProperty("content", text);
        } else {
            // Bot posts carry no display name, so the sender is folded into the body
            json.addProperty("content", "**" + sender + "**: " + text);
        }
        // Never ping roles or users from a mirrored game message
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        json.add("allowed_mentions", mentions);
        return json.toString();
    }

    private static HttpRequest.Builder jsonRequest(String url, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "ChatSphere (" + ModInfo.MODID + ")")
                .timeout(Duration.ofSeconds(15));
        if (authorization != null) builder.header("Authorization", authorization);
        return builder;
    }

    private void applyRetryAfter(String body) {
        long waitMs = 5000L;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("retry_after")) {
                waitMs = (long) (json.get("retry_after").getAsDouble() * 1000L) + 250L;
            }
        } catch (Exception ignored) {
        }
        waitMs = Math.max(1000L, Math.min(waitMs, 300_000L));
        outboundBlockedUntil = System.currentTimeMillis() + waitMs;
        nextPollAt = System.currentTimeMillis() + waitMs;
        noteError("rate limited, retrying in " + (waitMs / 1000) + "s");
    }

    /** Keeps one line per minute so a broken token cannot flood the log. */
    private void noteError(String message) {
        lastError = message;
        long now = System.currentTimeMillis();
        if (now - lastErrorAt < ERROR_LOG_INTERVAL_MS) return;
        lastErrorAt = now;
        LOGGER.warn("Discord bridge: {}", message);
    }

    private static String hint(int code) {
        return switch (code) {
            case 401 -> " (check the bot token)";
            case 403 -> " (the bot cannot post in that channel)";
            case 404 -> " (check the channel id or webhook url)";
            default -> "";
        };
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String msg = cause.getMessage();
        return msg == null || msg.isEmpty() ? cause.getClass().getSimpleName() : msg;
    }

    private static String str(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static long snowflake(JsonObject obj) {
        return parseSnowflake(str(obj, "id"));
    }

    private static long parseSnowflake(String id) {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** Discord usernames may contain anything; the chat row only needs a short label. */
    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) return "Discord";
        String cleaned = name.replace("\n", " ").replace("\r", " ").trim();
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, max - 1) + "…";
    }
}
