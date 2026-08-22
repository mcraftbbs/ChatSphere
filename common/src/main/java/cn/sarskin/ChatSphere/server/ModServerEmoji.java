package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.client.emoji.EmojiFileGuard;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ClientboundCustomEmojiPayload;
import cn.sarskin.ChatSphere.storage.ModStoragePaths;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared emoji store. Defense in depth: EmojiFileGuard validates uploads before disk, names whitelisted, per-folder cap, client re-validates. */
public class ModServerEmoji {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModServerEmoji");
    private static final Map<MinecraftServer, ModServerEmoji> INSTANCES = new ConcurrentHashMap<>();
    private static final List<String> EXTS = List.of("png", "gif");

    private final MinecraftServer server;
    private final Path dir;
    private final Map<UUID, Long> lastUploadAt = new HashMap<>();

    private ModServerEmoji(MinecraftServer server) {
        this.server = server;
        this.dir = ModStoragePaths.getServerDataDir().resolve("emojis");
    }

    public static ModServerEmoji getInstance(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> {
            ModServerEmoji emoji = new ModServerEmoji(s);
            emoji.ensureDir();
            return emoji;
        });
    }

    public static void removeServer(MinecraftServer server) {
        INSTANCES.remove(server);
    }

    private void ensureDir() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("Failed to create emoji dir {}", dir, e);
        }
    }

    /** Public emoji stay at the flat root (legacy layout); channel emoji go under channels/<hex id>/. */
    private Path scopeDir(String channelId) {
        if (channelId == null || channelId.isEmpty()) return dir;
        return dir.resolve("channels").resolve(EmojiFileGuard.channelDirName(channelId));
    }

    /** @return null on success, else a translatable error component. */
    public synchronized net.minecraft.network.chat.Component add(String channelId, String name, byte[] data) {
        String err = EmojiFileGuard.validate(name, data);
        if (err != null) return net.minecraft.network.chat.Component.translatable(err);
        int cap = ModServerConfig.CONFIG.emojiMaxTotal.get();
        if (cap > 0 && listNames(channelId).size() >= cap) {
            return net.minecraft.network.chat.Component.translatable("chatsphere.emoji.err_total", cap);
        }
        String ext = EmojiFileGuard.extensionFor(data);
        Path target = scopeDir(channelId).resolve(name + "." + ext);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            LOGGER.warn("Failed to save server emoji {}: {}", name, e.getMessage());
            return net.minecraft.network.chat.Component.translatable("chatsphere.emoji.err_save");
        }
        return null;
    }

    public synchronized net.minecraft.network.chat.Component delete(String channelId, String name) {
        if (name == null || !EmojiFileGuard.NAME.matcher(name).matches()) {
            return net.minecraft.network.chat.Component.translatable("chatsphere.emoji.err_invalid_name");
        }
        boolean removed = false;
        for (String ext : EXTS) {
            try {
                removed |= Files.deleteIfExists(scopeDir(channelId).resolve(name + "." + ext));
            } catch (IOException e) {
                LOGGER.warn("Failed to delete emoji {}: {}", name, e.getMessage());
            }
        }
        return removed ? null : net.minecraft.network.chat.Component.translatable("chatsphere.emoji.err_not_found");
    }

    public synchronized List<String> listNames(String channelId) {
        Path scope = scopeDir(channelId);
        List<String> names = new ArrayList<>();
        try (var stream = Files.list(scope)) {
            stream.filter(p -> EXTS.stream().anyMatch(ext -> p.getFileName().toString().endsWith("." + ext)))
                    .map(p -> stripExt(p.getFileName().toString()))
                    .filter(n -> EmojiFileGuard.NAME.matcher(n).matches())
                    .sorted(Comparator.naturalOrder())
                    .forEach(names::add);
        } catch (IOException e) {
            LOGGER.error("Failed to list emoji dir {}", scope, e);
        }
        return names;
    }

    public synchronized byte[] load(String channelId, String name) {
        if (name == null || !EmojiFileGuard.NAME.matcher(name).matches()) return null;
        for (String ext : EXTS) {
            Path p = scopeDir(channelId).resolve(name + "." + ext);
            if (Files.isRegularFile(p)) {
                try {
                    return Files.readAllBytes(p);
                } catch (IOException e) {
                    LOGGER.warn("Failed to read emoji {}: {}", name, e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    public void broadcastAdd(String channelId, String name, byte[] data) {
        ClientboundCustomEmojiPayload payload =
                new ClientboundCustomEmojiPayload(ClientboundCustomEmojiPayload.Action.ADD, name, channelId, data);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundCustomPayloadPacket(ClientboundCustomEmojiPayload.ID, payload.toBuf()));
        }
    }

    public void broadcastDelete(String channelId, String name) {
        ClientboundCustomEmojiPayload payload =
                new ClientboundCustomEmojiPayload(ClientboundCustomEmojiPayload.Action.DELETE, name, channelId, new byte[0]);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundCustomPayloadPacket(ClientboundCustomEmojiPayload.ID, payload.toBuf()));
        }
    }

    /** Push every server emoji (public + all channels) to one player (join / SYNC_REQUEST). */
    public void syncTo(ServerPlayer player) {
        for (String name : listNames("")) {
            byte[] data = load("", name);
            if (data == null) continue;
            player.connection.send(new ClientboundCustomPayloadPacket(ClientboundCustomEmojiPayload.ID,
                    new ClientboundCustomEmojiPayload(ClientboundCustomEmojiPayload.Action.ADD, name, "", data).toBuf()));
        }
        Path channelsDir = dir.resolve("channels");
        if (!Files.isDirectory(channelsDir)) return;
        try (var stream = Files.list(channelsDir)) {
            for (Path sub : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(sub)) continue;
                String channelId = EmojiFileGuard.channelDirDecode(sub.getFileName().toString());
                if (channelId.isEmpty()) continue;
                for (String name : listNames(channelId)) {
                    byte[] data = load(channelId, name);
                    if (data == null) continue;
                    player.connection.send(new ClientboundCustomPayloadPacket(ClientboundCustomEmojiPayload.ID,
                            new ClientboundCustomEmojiPayload(ClientboundCustomEmojiPayload.Action.ADD, name, channelId, data).toBuf()));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list channels emoji dir {}", channelsDir, e);
        }
    }

    /** Per-player upload cooldown. Returns remaining millis, or 0 when allowed. */
    public synchronized long uploadCooldownRemaining(UUID uuid) {
        if (uuid == null) return 0;
        int secs = ModServerConfig.CONFIG.emojiUploadCooldownSeconds.get();
        if (secs <= 0) return 0;
        Long last = lastUploadAt.get(uuid);
        if (last == null) return 0;
        long remain = secs * 1000L - (System.currentTimeMillis() - last);
        if (remain <= 0) {
            lastUploadAt.remove(uuid);
            return 0;
        }
        return remain;
    }

    public synchronized void recordUpload(UUID uuid) {
        if (uuid != null) lastUploadAt.put(uuid, System.currentTimeMillis());
        if (lastUploadAt.size() > 512) {
            long cutoff = System.currentTimeMillis() - 3600_000L;
            lastUploadAt.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }

    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
