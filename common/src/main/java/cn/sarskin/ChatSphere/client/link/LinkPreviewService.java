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
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "ChatSphere link preview")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
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
            LinkPreviewCache.markFailed(url);
            return;
        }
        LinkPreviewCache.put(url, preview, thumb);
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

    private static byte[] fetchImage(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "ChatSphere link preview")
                    .header("Accept", "image/*")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                byte[] data = readLimited(in, MAX_IMAGE);
                return isImage(data) ? data : null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isImage(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return true;
        return d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
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
