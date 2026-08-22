package cn.sarskin.ChatSphere.client.emoji;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.platform.PlatformPaths;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NodeList;

/**
 * User-uploaded emoji (local-first); tokens stay plain text so receivers without the file just see ":name:".
 */
public final class CustomEmojiRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-CustomEmoji");
    private static final Path DIR = PlatformPaths.configDir().resolve("chatsphere").resolve("emojis");
    /** Server-shared emoji; managed by server sync. Public lives at the srv root (legacy layout), channels under srv/channels/<hex id>/. */
    private static final Path SERVER_DIR = DIR.resolve("srv");
    private static final Path CHANNELS_DIR = SERVER_DIR.resolve("channels");
    /** Keyed by scope: local=name, public="\0"+name, channel=channelId+"\0"+name. */
    private static final Map<String, CustomEmoji> BY_SHORTCODE = new LinkedHashMap<>();
    private static boolean scanned;
    /** Channel the chat screen is showing; visibility filters on this. */
    private static String currentChannel = "";

    /** Inline render height — QQ-style big, ~5x the ~10px text line. */
    public static final int RENDER_H = 48;
    /** Wide images are clamped so a single emoji never swallows a whole line. */
    public static final int MAX_RENDER_W = 96;
    public static final List<String> IMAGE_EXTS = List.of("png", "gif");

    private static final Pattern TOKEN = Pattern.compile(":([a-zA-Z0-9_+-]+):");
    private static final Pattern SHORTCODE = Pattern.compile("[a-zA-Z0-9_+-]+");

    private static String keyFor(String channelId, String name) {
        if (channelId == null) return name;
        return channelId + "\0" + name;
    }

    /** Resolution: local > current channel > public. */
    private static CustomEmoji resolve(String name) {
        CustomEmoji e = BY_SHORTCODE.get(name);
        if (e != null) return e;
        e = BY_SHORTCODE.get(currentChannel + "\0" + name);
        if (e != null) return e;
        return BY_SHORTCODE.get("\0" + name);
    }

    private CustomEmojiRegistry() {
    }

    public static Path dir() {
        return DIR;
    }

    public static boolean isScanned() {
        return scanned;
    }

    public static boolean enabled() {
        return ModClientConfig.CONFIG.customEmojiEnabled.get();
    }

    /** Channel shown by the chat screen. */
    public static void setCurrentChannel(String channelId) {
        currentChannel = channelId == null ? "" : channelId;
    }

    public static String currentChannel() {
        return currentChannel;
    }

    /** (Re)scan folders and (re)upload textures; render thread only. */
    public static synchronized void scan() {
        clear();
        if (!enabled()) {
            scanned = false;
            return;
        }
        try {
            Files.createDirectories(DIR);
            Files.createDirectories(SERVER_DIR);
        } catch (IOException e) {
            LOGGER.error("Failed to create emoji dirs {}", DIR, e);
        }
        Minecraft mc = Minecraft.getInstance();
        // Server-shared first, then local: a local file with the same name wins.
        localLoaded = 0;
        scanDir(mc, SERVER_DIR, "");
        scanChannelDirs(mc);
        scanDir(mc, DIR, null);
        scanned = true;
        LOGGER.info("Custom emojis loaded: {} ({} server-shared)", BY_SHORTCODE.size(),
                BY_SHORTCODE.values().stream().filter(CustomEmoji::serverSynced).count());
    }

    private static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (CustomEmoji old : BY_SHORTCODE.values()) {
            mc.getTextureManager().release(old.texture());
        }
        BY_SHORTCODE.clear();
    }

    private static void scanDir(Minecraft mc, Path dir, String channelId) {
        try (var stream = Files.list(dir)) {
            stream.filter(p -> isImageFile(p.getFileName().toString()))
                    .sorted()
                    .forEach(p -> loadFile(mc, p, channelId));
        } catch (IOException e) {
            LOGGER.error("Failed to list emoji dir {}", dir, e);
        }
    }

    /** Each srv/channels/<hex>/ subdir is one channel scope. */
    private static void scanChannelDirs(Minecraft mc) {
        try (var stream = Files.list(CHANNELS_DIR)) {
            for (Path sub : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(sub)) continue;
                String channelId = EmojiFileGuard.channelDirDecode(sub.getFileName().toString());
                if (channelId.isEmpty()) continue;
                scanDir(mc, sub, channelId);
            }
        } catch (IOException e) {
            LOGGER.debug("No channel emoji dirs yet: {}", e.getMessage());
        }
    }

    public static synchronized void ensureScanned() {
        if (enabled() && scanned) return;
        scan();
    }

    private static int localLoaded;

    private static void loadFile(Minecraft mc, Path p, String channelId) {
        String fn = p.getFileName().toString();
        String name = stripExt(fn);
        if (!SHORTCODE.matcher(name).matches()) {
            LOGGER.warn("Emoji file '{}' skipped: name must be [a-zA-Z0-9_+-]", fn);
            return;
        }
        if (EmojiRegistry.byShortcode(":" + name + ":") != null) {
            LOGGER.warn("Emoji file '{}' skipped: collides with a built-in shortcode", fn);
            return;
        }
        if (channelId == null) {
            int cap = ModClientConfig.CONFIG.emojiLocalMaxTotal.get();
            if (cap > 0 && localLoaded >= cap) {
                LOGGER.debug("Local emoji '{}' skipped: limit {} reached", fn, cap);
                return;
            }
        }
        try {
            long size = Files.size(p);
            if (size > EmojiFileGuard.MAX_BYTES) {
                LOGGER.warn("Emoji '{}' skipped: {} bytes > {} limit", name, size, EmojiFileGuard.MAX_BYTES);
                return;
            }
            byte[] data = Files.readAllBytes(p);
            String gerr = EmojiFileGuard.validate(name, data);
            if (gerr != null) {
                LOGGER.warn("Emoji '{}' rejected: {}", fn, gerr);
                return;
            }
            boolean gif = "gif".equals(EmojiFileGuard.extensionFor(data));
            NativeImage img;
            int frames = 1;
            int[] delays = new int[0];
            int frameW;
            int frameH;
            if (gif) {
                // Sprite-sheet decode via the pure-Java ImageIO reader; never the native STB GIF parser.
                GifSheet sheet = decodeGif(data);
                if (sheet == null) {
                    LOGGER.warn("Emoji '{}' skipped: invalid image", fn);
                    return;
                }
                img = sheet.image;
                frames = sheet.frames;
                delays = sheet.delays;
                frameW = sheet.frameW;
                frameH = sheet.frameH;
            } else {
                img = NativeImage.read(new ByteArrayInputStream(data));
                if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) {
                    if (img != null) img.close();
                    LOGGER.warn("Emoji '{}' skipped: invalid image", fn);
                    return;
                }
                if (img.getWidth() > EmojiFileGuard.MAX_DIM || img.getHeight() > EmojiFileGuard.MAX_DIM) {
                    img.close();
                    LOGGER.warn("Emoji '{}' skipped: decoded {}x{} exceeds {}px", fn,
                            img.getWidth(), img.getHeight(), EmojiFileGuard.MAX_DIM);
                    return;
                }
                frameW = img.getWidth();
                frameH = img.getHeight();
            }
            int w = frameW;
            int h = frameH;
            // Keep aspect ratio inside the RENDER_H x MAX_RENDER_W box.
            double ratio = (double) w / h;
            int rw = (int) Math.round(RENDER_H * ratio);
            int rh = RENDER_H;
            if (rw > MAX_RENDER_W) {
                rw = MAX_RENDER_W;
                rh = Math.max(12, (int) Math.round(MAX_RENDER_W / ratio));
            }
            rw = Math.max(12, rw);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "emoji_custom/" + name);
            DynamicTexture tex = new DynamicTexture(img);
            mc.getTextureManager().register(id, tex);
            BY_SHORTCODE.put(keyFor(channelId, name), new CustomEmoji(name, id, rw, rh, size, channelId != null, channelId, frames, delays, frameW, frameH));
            if (channelId == null) localLoaded++;
        } catch (Exception e) {
            LOGGER.warn("Emoji '{}' rejected: {}", fn, e.getMessage());
        }
    }

    private record GifSheet(NativeImage image, int frameW, int frameH, int frames, int[] delays) {
    }

    /** Decode a GIF into one sprite-sheet texture; caps re-checked here, independent of the guard. */
    private static GifSheet decodeGif(byte[] data) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) throw new IOException("no GIF image reader");
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, false, false);
                int frames = reader.getNumImages(true);
                if (frames < 1 || frames > EmojiFileGuard.MAX_FRAMES) {
                    throw new IOException("frame count " + frames);
                }
                int lsdW = (data[6] & 0xFF) | ((data[7] & 0xFF) << 8);
                int lsdH = (data[8] & 0xFF) | ((data[9] & 0xFF) << 8);
                // delta-frame GIFs store raw rasters of varying size at (left, top) offsets;
                // composite each onto one canvas cell
                int[] fw = new int[frames];
                int[] fh = new int[frames];
                int[] fx = new int[frames];
                int[] fy = new int[frames];
                int canvasW = lsdW;
                int canvasH = lsdH;
                for (int f = 0; f < frames; f++) {
                    fw[f] = reader.getWidth(f);
                    fh[f] = reader.getHeight(f);
                    int[] off = frameOffset(reader, f);
                    fx[f] = off[0];
                    fy[f] = off[1];
                    canvasW = Math.max(canvasW, fx[f] + fw[f]);
                    canvasH = Math.max(canvasH, fy[f] + fh[f]);
                }
                int maxDim = frames > 1 ? EmojiFileGuard.MAX_ANIM_DIM : EmojiFileGuard.MAX_DIM;
                if (canvasW <= 0 || canvasH <= 0 || canvasW > maxDim || canvasH > maxDim) {
                    throw new IOException("frame size " + canvasW + "x" + canvasH);
                }
                if (canvasW * frames > EmojiFileGuard.MAX_SHEET_W) {
                    throw new IOException("sheet width " + canvasW * frames);
                }
                int[] delays = new int[frames];
                NativeImage sheet = new NativeImage(canvasW * frames, canvasH, true);
                BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
                int[] prevRect = new int[4];
                String prevDispose = "";
                try {
                    for (int f = 0; f < frames; f++) {
                        BufferedImage frame = reader.read(f);
                        if (frame == null || frame.getWidth() != fw[f] || frame.getHeight() != fh[f]) {
                            throw new IOException("frame " + f + " size mismatch");
                        }
                        String dispose = disposal(reader, f);
                        if ("restoreToBackground".equals(prevDispose)) {
                            canvas.getGraphics().clearRect(prevRect[0], prevRect[1], prevRect[2], prevRect[3]);
                        }
                        prevRect[0] = fx[f];
                        prevRect[1] = fy[f];
                        prevRect[2] = fw[f];
                        prevRect[3] = fh[f];
                        prevDispose = dispose;
                        canvas.getGraphics().drawImage(frame, fx[f], fy[f], null);
                        delays[f] = frameDelayMs(reader, f);
                        int[] argb = canvas.getRGB(0, 0, canvasW, canvasH, null, 0, canvasW);
                        int ox = f * canvasW;
                        for (int y = 0; y < canvasH; y++) {
                            for (int x = 0; x < canvasW; x++) {
                                int c = argb[y * canvasW + x];
                                int a = (c >>> 24) & 0xFF;
                                int r = (c >>> 16) & 0xFF;
                                int g = (c >>> 8) & 0xFF;
                                int b = c & 0xFF;
                                sheet.setPixelRGBA(ox + x, y, (a << 24) | (b << 16) | (g << 8) | r);
                            }
                        }
                    }
                } catch (Exception e) {
                    sheet.close();
                    throw e;
                }
                return new GifSheet(sheet, canvasW, canvasH, frames, delays);
            } finally {
                reader.dispose();
            }
        }
    }

    /** GIF frame delay in ms from the graphic-control extension; 100 when absent or unparsable. */
    private static int frameDelayMs(ImageReader reader, int index) {
        try {
            IIOMetadata meta = reader.getImageMetadata(index);
            if (meta == null) return 100;
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
            NodeList nodes = root.getElementsByTagName("GraphicControlExtension");
            if (nodes.getLength() > 0) {
                String delay = ((IIOMetadataNode) nodes.item(0)).getAttribute("delayTime");
                if (!delay.isEmpty()) {
                    return Math.max(10, Integer.parseInt(delay) * 10);
                }
            }
        } catch (Exception ignored) {
        }
        return 100;
    }

    /** Frame raster offset from the image-descriptor metadata; 0 when absent or unparsable. */
    private static int[] frameOffset(ImageReader reader, int index) {
        try {
            IIOMetadata meta = reader.getImageMetadata(index);
            if (meta == null) return new int[]{0, 0};
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
            NodeList nodes = root.getElementsByTagName("ImageDescriptor");
            if (nodes.getLength() > 0) {
                IIOMetadataNode d = (IIOMetadataNode) nodes.item(0);
                int left = Integer.parseInt(d.getAttribute("imageLeftPosition"));
                int top = Integer.parseInt(d.getAttribute("imageTopPosition"));
                return new int[]{left, top};
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }

    /** GIF disposal method; restoreToPrevious is treated as doNotDispose (rare, visually close). */
    private static String disposal(ImageReader reader, int index) {
        try {
            IIOMetadata meta = reader.getImageMetadata(index);
            if (meta == null) return "";
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
            NodeList nodes = root.getElementsByTagName("GraphicControlExtension");
            if (nodes.getLength() > 0) {
                return ((IIOMetadataNode) nodes.item(0)).getAttribute("disposalMethod");
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static boolean isImageFile(String fileName) {
        String ext = extOf(fileName);
        return ext != null && IMAGE_EXTS.contains(ext);
    }

    private static String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Copy a picked file into the local emoji folder and rescan. Returns null on success, else a translatable error. */
    public static synchronized Component copyFrom(Path src) {
        if (src == null || !Files.isRegularFile(src)) return Component.translatable("chatsphere.emoji.err_missing");
        String fn = src.getFileName().toString();
        String ext = extOf(fn);
        if (ext == null || !IMAGE_EXTS.contains(ext)) return Component.translatable("chatsphere.emoji.err_file");
        String name = stripExt(fn);
        if (EmojiRegistry.byShortcode(":" + name + ":") != null) return Component.translatable("chatsphere.emoji.err_dup");
        int cap = ModClientConfig.CONFIG.emojiLocalMaxTotal.get();
        if (cap > 0 && countLocal() >= cap) return Component.translatable("chatsphere.emoji.err_total_local", cap);
        try {
            byte[] data = Files.readAllBytes(src);
            String err = EmojiFileGuard.validate(name, data);
            if (err != null) return Component.translatable(err);
            // real extension comes from the magic number, not the file name
            String realExt = EmojiFileGuard.extensionFor(data);
            Files.createDirectories(DIR);
            Path target = DIR.resolve(name + "." + realExt);
            if (Files.exists(target)) return Component.translatable("chatsphere.emoji.err_exists");
            Files.write(target, data);
        } catch (IOException e) {
            return Component.translatable("chatsphere.emoji.err_upload", e.getMessage());
        }
        scan();
        return null;
    }

    /** Delete one local emoji file; server-shared ones are handled separately. */
    public static synchronized Component delete(String shortcode) {        CustomEmoji emoji = BY_SHORTCODE.get(shortcode);
        if (emoji == null) return Component.translatable("chatsphere.emoji.err_not_found");
        if (emoji.serverSynced()) return Component.translatable("chatsphere.emoji.err_server_delete");
        try {
            Files.deleteIfExists(DIR.resolve(shortcode + ".png"));
            Files.deleteIfExists(DIR.resolve(shortcode + ".gif"));
        } catch (IOException e) {
            return Component.translatable("chatsphere.emoji.err_delete", e.getMessage());
        }
        scan();
        return null;
    }

    /** Read the local file bytes of a user-uploaded emoji (root folder only, never srv/). */
    public static byte[] readLocalBytes(String shortcode) {
        if (shortcode == null) return null;
        for (String ext : IMAGE_EXTS) {
            Path p = DIR.resolve(shortcode + "." + ext);
            if (Files.isRegularFile(p)) {
                try {
                    return Files.readAllBytes(p);
                } catch (IOException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Path serverScopeDir(String channelId) {
        if (channelId == null || channelId.isEmpty()) return SERVER_DIR;
        return CHANNELS_DIR.resolve(EmojiFileGuard.channelDirName(channelId));
    }

    /** Local (root folder) emoji count, for the client cap. */
    public static int countLocal() {
        int n = 0;
        try (var stream = Files.list(DIR)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (isImageFile(p.getFileName().toString())) n++;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to count emoji dir {}", DIR, e);
        }
        return n;
    }

    /** Server ADD; re-validated before writing (defense in depth). Client thread. */
    public static synchronized Component receiveServerAdd(String name, byte[] data, String channelId) {
        if (!enabled()) return null;
        String err = EmojiFileGuard.validate(name, data);
        if (err != null) {
            LOGGER.warn("Rejected server emoji '{}': {}", name, err);
            return Component.translatable(err);
        }
        String realExt = EmojiFileGuard.extensionFor(data);
        Path scope = serverScopeDir(channelId);
        try {
            Files.createDirectories(scope);
            Files.write(scope.resolve(name + "." + realExt), data);
        } catch (IOException e) {
            LOGGER.warn("Failed to save server emoji {}: {}", name, e.getMessage());
            return Component.translatable("chatsphere.emoji.err_save");
        }
        scan();
        return null;
    }

    /** Server DELETE; client thread. */
    public static synchronized void receiveServerDelete(String name, String channelId) {
        if (!enabled()) return;
        Path scope = serverScopeDir(channelId);
        try {
            Files.deleteIfExists(scope.resolve(name + ".png"));
            Files.deleteIfExists(scope.resolve(name + ".gif"));
        } catch (IOException e) {
            LOGGER.warn("Failed to delete server emoji {}: {}", name, e.getMessage());
        }
        scan();
    }

    /** Server emoji list push; fired once on login. */
    public static void requestSync() {
        if (!enabled()) return;
        sendToServer(new cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload(
                cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload.Action.SYNC_REQUEST, "", "", new byte[0]));
    }

    /** Upload a local file (validated locally first). channelId "" = public. */
    public static void uploadToServer(String name, byte[] data, String channelId) {
        String err = EmojiFileGuard.validate(name, data);
        if (err != null) {
            LOGGER.warn("Rejected upload '{}': {}", name, err);
            return;
        }
        sendToServer(new cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload(
                cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload.Action.ADD, name,
                channelId == null ? "" : channelId, data));
    }

    public static void deleteFromServer(String name, String channelId) {
        sendToServer(new cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload(
                cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload.Action.DELETE, name,
                channelId == null ? "" : channelId, new byte[0]));
    }

    private static void sendToServer(cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null || mc.getConnection().getConnection() == null) return;
        mc.getConnection().getConnection().send(
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(p));
    }

    public static List<CustomEmoji> list() {
        return List.copyOf(BY_SHORTCODE.values());
    }

    /** Local plus current-channel-visible server emoji, deduped by shortcode. */
    public static List<CustomEmoji> listVisible() {
        List<CustomEmoji> out = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (CustomEmoji e : BY_SHORTCODE.values()) {
            if (resolve(e.shortcode()) != e) continue;
            if (seen.add(e.shortcode())) out.add(e);
        }
        return out;
    }

    public static CustomEmoji byShortcode(String shortcode) {
        return shortcode == null ? null : resolve(shortcode);
    }

    public static boolean isCustom(String token) {
        if (token == null || token.length() < 3 || token.charAt(0) != ':' || token.charAt(token.length() - 1) != ':') {
            return false;
        }
        return resolve(token.substring(1, token.length() - 1)) != null;
    }

    public static boolean containsToken(Component comp) {
        return comp != null && containsToken(comp.getString());
    }

    public static boolean containsToken(String text) {
        if (text == null || text.isEmpty()) return false;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            if (resolve(m.group(1)) != null) return true;
        }
        return false;
    }

    /** Tallest custom emoji height in the text; 0 when none. */
    public static int lineHeightFor(String text) {
        if (text == null || text.isEmpty()) return 0;
        int max = 0;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            CustomEmoji e = resolve(m.group(1));
            if (e != null && e.height() > max) max = e.height();
        }
        return max;
    }

    /** Width of a component when custom emoji tokens render as pictures. */
    public static int width(Font font, Component comp) {
        return segmentWidth(font, comp.getString());
    }

    private static int segmentWidth(Font font, String text) {
        int w = 0;
        int last = 0;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            CustomEmoji e = resolve(m.group(1));
            if (e == null) continue;
            w += font.width(text.substring(last, m.start()));
            w += e.width();
            last = m.end();
        }
        w += font.width(text.substring(last));
        return w;
    }

    /** Component variant of renderText. */
    public static void renderLine(GuiGraphics g, Component comp, int x, int y, int color, boolean shadow) {
        renderText(g, comp.getString(), x, y, color, shadow);
    }

    /** Draw text, replacing visible custom emoji tokens with their textures. */
    public static void renderText(GuiGraphics g, String text, int x, int y, int color, boolean shadow) {
        Font font = Minecraft.getInstance().font;
        int last = 0;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            CustomEmoji e = resolve(m.group(1));
            if (e == null) continue;
            if (m.start() > last) {
                String seg = text.substring(last, m.start());
                g.drawString(font, Component.literal(seg), x, y, color, shadow);
                x += font.width(seg);
            }
            e.blit(g, x, y, e.width(), e.height());
            x += e.width();
            last = m.end();
        }
        if (last < text.length()) {
            String seg = text.substring(last);
            g.drawString(font, Component.literal(seg), x, y, color, shadow);
            x += font.width(seg);
        }
    }
}
