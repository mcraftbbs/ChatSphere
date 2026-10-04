package cn.sarskin.ChatSphere.client.image;

import cn.sarskin.ChatSphere.client.link.LinkPreviewService;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.network.ServerboundChatImagePayload;
import cn.sarskin.ChatSphere.server.ModServerImages;
import net.minecraft.client.Minecraft;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Chat image upload and fetch; callers set the sinks once a screen is open. */
public final class ChatImageClient {
    private static final int CHUNK = ModServerImages.CHUNK_BYTES;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> URLs_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<String, byte[][]> INCOMING = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PENDING_UPLOADS = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ChatSphere-ChatImage");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile Consumer<String> tokenSink;
    private static volatile Consumer<String> messageSink;

    private ChatImageClient() {}

    public static void setTokenSink(Consumer<String> sink) {
        tokenSink = sink;
    }

    public static void setMessageSink(Consumer<String> sink) {
        messageSink = sink;
    }

    /** Validates then sends every chunk; returns a lang key on rejection, null when the chunks went out. */
    public static String upload(byte[] data, String sourceUrl, int[] dims) {
        if (!ModClientConfig.CONFIG.chatImagesEnabled.get()) return ChatImageGuard.ERR_DISABLED;
        int[] size = dims != null && dims.length >= 2 ? dims : new int[2];
        String error = ChatImageGuard.validate(data, size);
        if (error != null) return error;
        int count = Math.max(1, (data.length + CHUNK - 1) / CHUNK);
        if (count > ModServerImages.MAX_CHUNKS) return ChatImageGuard.ERR_SIZE;
        String id = randomId();
        PENDING_UPLOADS.put(id, data);
        for (int i = 0; i < count; i++) {
            int from = i * CHUNK;
            int to = Math.min(data.length, from + CHUNK);
            ServerboundChatImagePayload.sendToServer(new ServerboundChatImagePayload(
                    ServerboundChatImagePayload.Action.UPLOAD, id, i, count, size[0], size[1],
                    sourceUrl == null ? "" : sourceUrl, Arrays.copyOfRange(data, from, to)));
        }
        return null;
    }

    /** True when the whole input is one allowed image URL and the upload was started; the URL must not be sent. */
    public static boolean uploadFromUrl(String url) {
        if (!ModClientConfig.CONFIG.chatImagesEnabled.get()) return false;
        if (url == null || !url.matches("^https?://\\S+$")) return false;
        if (!LinkPreviewService.allowed(url) || !URLs_IN_FLIGHT.add(url)) return false;
        POOL.execute(() -> {
            try {
                byte[] data = LinkPreviewService.downloadImage(url);
                if (data == null) {
                    notifyLater(ChatImageGuard.ERR_FORMAT);
                    return;
                }
                int[] dims = new int[2];
                String error = ChatImageGuard.validate(data, dims);
                if (error == null) error = upload(data, url, dims);
                if (error != null) notifyLater(error);
            } catch (Exception e) {
                notifyLater(ChatImageGuard.ERR_FORMAT);
            } finally {
                URLs_IN_FLIGHT.remove(url);
            }
        });
        return true;
    }

    /** Asks the server for an image; repeated calls for the same id are dropped. */
    public static void request(String id) {
        if (id == null || id.isEmpty() || ChatImage.LOCAL.equals(id)) return;
        if (ChatImageCache.cached(id) || ChatImageCache.requested(id) || ChatImageCache.missing(id)) return;
        ChatImageCache.markRequested(id);
        ServerboundChatImagePayload.sendToServer(new ServerboundChatImagePayload(
                ServerboundChatImagePayload.Action.REQUEST, id, 0, 1, 0, 0, "", new byte[0]));
    }

    public static void handleAccepted(String id, int width, int height) {
        byte[] local = PENDING_UPLOADS.remove(id);
        if (local != null) ChatImageCache.put(id, local);
        insertLater(ChatImage.token(id, width, height));
    }

    public static void handleData(String id, int partIndex, int partCount, byte[] bytes) {
        if (id == null || partCount < 1 || partCount > ModServerImages.MAX_CHUNKS
                || partIndex < 0 || partIndex >= partCount) return;
        byte[][] parts = INCOMING.compute(id, (key, current) ->
                current != null && current.length == partCount ? current : new byte[partCount][]);
        synchronized (parts) {
            parts[partIndex] = bytes;
            for (byte[] part : parts) {
                if (part == null) return;
            }
        }
        INCOMING.remove(id);
        int size = 0;
        for (byte[] part : parts) size += part.length;
        byte[] data = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, data, offset, part.length);
            offset += part.length;
        }
        if (ChatImageGuard.validate(data, new int[2]) != null) {
            ChatImageCache.markMissing(id);
            return;
        }
        ChatImageCache.put(id, data);
    }

    public static void handleFailed(String id, String reasonKey) {
        if (id != null) {
            PENDING_UPLOADS.remove(id);
            ChatImageCache.markMissing(id);
        }
        notifyLater(reasonKey);
    }

    public static void reset() {
        INCOMING.clear();
        PENDING_UPLOADS.clear();
        URLs_IN_FLIGHT.clear();
    }

    /** 16 hex chars, the shape the server validates. */
    private static String randomId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(16);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    /** Sinks may be reached from the download pool, so hand them to the client thread. */
    private static void insertLater(String token) {
        Consumer<String> sink = tokenSink;
        if (sink == null) return;
        runOnClient(() -> sink.accept(token));
    }

    private static void notifyLater(String langKey) {
        Consumer<String> sink = messageSink;
        if (sink == null || langKey == null) return;
        runOnClient(() -> sink.accept(langKey));
    }

    private static void runOnClient(Runnable task) {
        try {
            Minecraft.getInstance().execute(task);
        } catch (Throwable ignored) {
        }
    }
}
