package cn.sarskin.ChatSphere.client.link;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches link cards for URLs that are on screen. Everything happens off the render thread; callers
 * only ask for a URL and read whatever the cache already holds.
 */
public final class LinkPreviewService {
    private static final int MAX_HTML = 256 * 1024;
    private static final int MAX_IMAGE = 512 * 1024;
    private static final int MAX_PENDING = 48;
    private static final Pattern BILI = Pattern.compile("(?i)^https?://(?:www\\.)?bilibili\\.com/video/(BV[0-9A-Za-z]+)");
    private static final Pattern META = Pattern.compile("(?is)<meta\\s+([^>]*?)>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern ATTR = Pattern.compile("(?is)(property|name|content)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");

    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ChatSphere-LinkPreview");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private LinkPreviewService() {}

    /** Effective card mode; a server that forbids previews wins over the client setting. */
    public static int mode() {
        if (!ModServerConfig.CONFIG.linkPreviewEnabled.get()) return 0;
        return ModClientConfig.CONFIG.linkPreviewMode.get();
    }

    public static LinkPreview preview(String url) {
        return LinkPreviewCache.get(url);
    }

    /** URLs of one message that deserve a card: mode on, allowed, capped by the client setting. */
    public static List<String> cardUrls(String text) {
        if (mode() == 0 || text == null || text.isEmpty()) return List.of();
        int max = Math.max(1, ModClientConfig.CONFIG.linkPreviewMaxPerMessage.get());
        List<String> out = new ArrayList<>();
        for (String url : cn.sarskin.ChatSphere.client.RichTextParser.findUrls(text, max)) {
            if (!allowed(url)) continue;
            // a failed fetch would otherwise reserve height for a card that never fills
            if (LinkPreviewCache.get(url) == null && LinkPreviewCache.failedRecently(url)) continue;
            out.add(url);
        }
        return out;
    }

    /** Called for URLs of on-screen messages only, so nothing is fetched for history that is off screen. */
    public static void request(String url) {
        if (mode() == 0 || url == null || url.isEmpty()) return;
        if (!allowed(url)) return;
        if (LinkPreviewCache.known(url) || LinkPreviewCache.failedRecently(url)) return;
        if (IN_FLIGHT.size() >= MAX_PENDING || !IN_FLIGHT.add(url)) return;
        POOL.execute(() -> {
            try {
                fetch(url);
            } catch (Exception e) {
                LinkPreviewCache.markFailed(url);
            } finally {
                IN_FLIGHT.remove(url);
            }
        });
    }

    /** Scheme, loopback and both whitelists; a blocked URL keeps its plain link. */
    public static boolean allowed(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return false;
        String host = uri.getHost();
        if (host == null || host.isEmpty() || privateHost(host)) return false;
        if (!matchesAny(ModClientConfig.CONFIG.urlLinkFilter.get(), url)) return false;
        String serverList = ModServerConfig.CONFIG.linkPreviewAllowedDomains.get();
        if (serverList != null && !serverList.isBlank() && !matchesAny(splitList(serverList), url)) return false;
        return true;
    }

    private static boolean matchesAny(List<String> patterns, String url) {
        if (patterns == null || patterns.isEmpty()) return true;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            try {
                if (Pattern.compile(pattern.trim(), Pattern.CASE_INSENSITIVE).matcher(url).find()) return true;
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    static List<String> splitList(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[,\\r\\n]")) {
            if (!part.isBlank()) out.add(part.trim());
        }
        return out;
    }

    private static boolean privateHost(String host) {
        String h = host.toLowerCase();
        if (h.equals("localhost") || h.endsWith(".local") || h.equals("::1") || h.equals("[::1]")) return true;
        if (h.startsWith("127.") || h.startsWith("10.") || h.startsWith("192.168.") || h.startsWith("169.254.")) return true;
        if (h.startsWith("172.")) {
            String[] parts = h.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) return true;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return false;
    }

    private static void fetch(String url) throws Exception {
        if (bilibiliCard(url) != null) return;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            fallback(url);
            return;
        }
        String html;
        try (InputStream in = response.body()) {
            html = new String(readLimited(in, MAX_HTML), StandardCharsets.UTF_8);
        }
        LinkPreview preview = parse(url, html);
        byte[] thumb = null;
        if (!preview.imageUrl().isEmpty() && allowed(preview.imageUrl())) {
            thumb = fetchImage(preview.imageUrl());
        }
        if (preview.empty() && thumb == null) {
            fallback(url);
            return;
        }
        LinkPreviewCache.put(url, preview, thumb);
    }

    /** Bilibili video pages render their head with script, so ask the public view api instead. */
    private static LinkPreview bilibiliCard(String url) {
        java.util.regex.Matcher matcher = BILI.matcher(url);
        if (!matcher.find()) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.bilibili.com/x/web-interface/view?bvid=" + matcher.group(1)))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .header("Referer", "https://www.bilibili.com/")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            String json;
            try (InputStream in = response.body()) {
                json = new String(readLimited(in, MAX_HTML), StandardCharsets.UTF_8);
            }
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (root.has("code") && root.get("code").getAsInt() != 0) return null;
            if (!root.has("data") || !root.get("data").isJsonObject()) return null;
            com.google.gson.JsonObject data = root.getAsJsonObject("data");
            String title = data.has("title") ? data.get("title").getAsString() : "";
            String desc = data.has("desc") ? data.get("desc").getAsString() : "";
            String pic = data.has("pic") ? data.get("pic").getAsString() : "";
            if (title.isEmpty() && pic.isEmpty()) return null;
            byte[] thumb = null;
            if (!pic.isEmpty() && allowed(pic)) thumb = fetchImage(pic);
            String site = hostOf(url);
            if (data.has("owner") && data.get("owner").isJsonObject()) {
                String owner = data.getAsJsonObject("owner").has("name")
                        ? data.getAsJsonObject("owner").get("name").getAsString() : "";
                site = owner.isEmpty() ? site : owner + " 路 " + site;
            }
            LinkPreview preview = new LinkPreview(url, title, desc, pic, site);
            LinkPreviewCache.put(url, preview, thumb);
            return preview;
        } catch (Exception e) {
            return null;
        }
    }

    /** Card built from the URL alone, so a blocked or tag-less page still shows something. */
    private static void fallback(String url) {
        String host = hostOf(url);
        String path = "";
        try {
            URI uri = new URI(url);
            path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        } catch (Exception ignored) {
        }
        String title = path.isEmpty() ? host : host + path;
        if (title.length() > 120) title = title.substring(0, 120);
        LinkPreviewCache.put(url, new LinkPreview(url, title, "", "", host), null);
    }

    /** Minimal OpenGraph reader; falls back to the document title. */
    static LinkPreview parse(String url, String html) {
        String title = "";
        String description = "";
        String image = "";
        Matcher meta = META.matcher(html);
        while (meta.find()) {
            String tag = meta.group(1);
            String key = attr(tag, "property");
            if (key.isEmpty()) key = attr(tag, "name");
            String content = attr(tag, "content");
            if (key.isEmpty() || content.isEmpty()) continue;
            String lower = key.toLowerCase();
            switch (lower) {
                case "og:title" -> title = title.isEmpty() ? content : title;
                case "og:description" -> description = description.isEmpty() ? content : description;
                case "og:image", "og:image:url", "twitter:image" -> image = image.isEmpty() ? content : image;
                default -> {
                }
            }
        }
        if (title.isEmpty()) {
            Matcher titleTag = TITLE.matcher(html);
            if (titleTag.find()) title = titleTag.group(1);
        }
        return new LinkPreview(url, clean(title), clean(description), resolve(url, clean(image)), hostOf(url));
    }

    private static String attr(String tag, String name) {
        Matcher matcher = ATTR.matcher(tag);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                return value == null ? "" : value;
            }
        }
        return "";
    }

    private static String clean(String raw) {
        String text = raw.replaceAll("(?is)<[^>]*>", " ").trim();
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    private static String resolve(String base, String target) {
        if (target.isEmpty()) return "";
        try {
            return URI.create(base).resolve(target).toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? "" : host;
        } catch (Exception e) {
            return "";
        }
    }

    /** Downloads one image for the chat image uploader; null when the URL is blocked or not an image. */
    public static byte[] downloadImage(String url) {
        if (url == null || url.isEmpty() || !allowed(url)) return null;
        return fetchImage(url);
    }

    private static byte[] fetchImage(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "ChatSphere link preview")
                    .header("Accept", "image/jpeg,image/png,image/apng,image/*;q=0.5")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                byte[] data = readLimited(in, MAX_IMAGE);
                return toDrawable(data);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** PNG/JPEG pass through; WebP is re-encoded when a reader exists, otherwise null. */
    static byte[] toDrawable(byte[] data) {
        if (data == null || data.length == 0) return null;
        if (isPng(data)) return data;
        byte[] converted = cn.sarskin.ChatSphere.client.image.ChatImageGuard.pngBytes(data);
        return converted != null && converted.length > 0 && converted.length <= MAX_IMAGE ? converted : null;
    }

    static boolean isPng(byte[] d) {
        return d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
    }

    static boolean isJpeg(byte[] d) {
        return d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
    }

    static boolean isWebp(byte[] d) {
        return d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
    }

    private static byte[] readLimited(InputStream in, int limit) throws Exception {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int total = 0;
        int read;
        while (total < limit && (read = in.read(buffer)) > 0) {
            int keep = Math.min(read, limit - total);
            out.write(buffer, 0, keep);
            total += keep;
        }
        return out.toByteArray();
    }
}
