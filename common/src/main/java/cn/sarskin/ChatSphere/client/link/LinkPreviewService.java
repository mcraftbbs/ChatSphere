package cn.sarskin.ChatSphere.client.link;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches link cards for on-screen URLs off the render thread; callers only read the cache.
 * Extraction follows the web's own metadata standards rather than per-site APIs.
 */
public final class LinkPreviewService {
    private static final int MAX_HTML = 256 * 1024;
    private static final int MAX_JSON = 64 * 1024;
    private static final int MAX_IMAGE = 512 * 1024;
    private static final int MAX_PENDING = 48;
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    // Schema.org types that describe the page itself, not the publisher or the site
    private static final Set<String> CONTENT_TYPES = Set.of("videoobject", "article", "newsarticle",
            "blogposting", "socialmediaposting", "product", "recipe", "movie", "tvepisode", "creativework",
            "itempage", "techarticle", "report", "review", "book", "course", "softwareapplication");

    private static final Pattern META = Pattern.compile("(?is)<meta\\s+([^>]*?)>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern ATTR = Pattern.compile("(?is)([A-Za-z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");
    private static final Pattern JSON_LD = Pattern.compile("(?is)<script[^>]+application/ld\\+json[^>]*>(.*?)</script>");
    private static final Pattern OEMBED = Pattern.compile("(?is)<link[^>]+application/json\\+oembed[^>]*>");
    private static final Pattern IMG = Pattern.compile("(?is)<img\\s+([^>]*?)>");

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
        String h = host.toLowerCase(Locale.ROOT);
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

    private static String fetchHtml(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", UA)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            try (InputStream raw = response.body(); InputStream in = decompress(raw, response)) {
                return new String(readLimited(in, MAX_HTML), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** HttpClient hands the body over untouched, so gzip has to be unwrapped here. */
    private static InputStream decompress(InputStream in, HttpResponse<?> response) throws Exception {
        String encoding = response.headers().firstValue("content-encoding").orElse("");
        if (encoding.toLowerCase(Locale.ROOT).contains("gzip")) return new java.util.zip.GZIPInputStream(in);
        if (encoding.toLowerCase(Locale.ROOT).contains("deflate")) return new java.util.zip.InflaterInputStream(in);
        // Some servers gzip regardless of what was asked; the magic number gives it away
        java.io.PushbackInputStream probe = new java.io.PushbackInputStream(in, 2);
        byte[] head = new byte[2];
        int read = probe.read(head, 0, 2);
        if (read == 2) probe.unread(head, 0, 2);
        if (read == 2 && (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B) {
            return new java.util.zip.GZIPInputStream(probe);
        }
        return probe;
    }

    private static void fetch(String url) throws Exception {
        String html = fetchHtml(url);
        if (html == null) {
            fallback(url);
            return;
        }
        LinkPreview preview = parse(url, html);
        // oEmbed costs an extra request, so only ask when the page itself gave little away
        if (preview.empty() || preview.imageUrl().isEmpty()) {
            LinkPreview oembed = oembedCard(url, html);
            if (oembed != null) preview = merge(preview, oembed);
        }
        byte[] thumb = null;
        if (!preview.imageUrl().isEmpty() && allowed(preview.imageUrl())) thumb = fetchImage(preview.imageUrl());
        if (preview.empty() && thumb == null) {
            fallback(url);
            return;
        }
        LinkPreviewCache.put(url, preview, thumb);
    }

    /** Layered extraction: structured data, then the meta tags sites ship for crawlers. */
    static LinkPreview parse(String url, String html) {
        LinkPreview structured = jsonLdCard(url, html);
        LinkPreview meta = metaCard(url, html);
        LinkPreview merged = merge(structured, meta);
        if (merged == null) merged = new LinkPreview(url, "", "", "", "");
        String title = merged.title();
        if (title.isEmpty()) {
            Matcher titleTag = TITLE.matcher(html);
            if (titleTag.find()) title = clean(titleTag.group(1));
        }
        String image = merged.imageUrl();
        if (image.isEmpty()) image = firstImage(url, html);
        String site = merged.site().isEmpty() ? hostOf(url) : merged.site();
        return new LinkPreview(url, title, merged.description(), image, site);
    }

    /** Fills gaps in the first card from the second; both may be null. */
    private static LinkPreview merge(LinkPreview first, LinkPreview second) {
        if (first == null) return second;
        if (second == null) return first;
        return new LinkPreview(first.url(),
                first.title().isEmpty() ? second.title() : first.title(),
                first.description().isEmpty() ? second.description() : first.description(),
                first.imageUrl().isEmpty() ? second.imageUrl() : first.imageUrl(),
                first.site().isEmpty() ? second.site() : first.site());
    }

    /** schema.org JSON-LD; most modern sites embed the page's real title, blurb and cover here. */
    static LinkPreview jsonLdCard(String url, String html) {
        List<JsonObject> nodes = new ArrayList<>();
        Matcher matcher = JSON_LD.matcher(html);
        while (matcher.find() && nodes.size() < 200) {
            String body = matcher.group(1);
            if (body.length() > MAX_JSON) body = body.substring(0, MAX_JSON);
            try {
                collectNodes(JsonParser.parseString(body), nodes, 0);
            } catch (Exception ignored) {
            }
        }
        // two passes: content nodes first, publisher or site nodes only as a last resort
        for (int pass = 0; pass < 2; pass++) {
            for (JsonObject node : nodes) {
                boolean content = CONTENT_TYPES.contains(typeOf(node));
                if ((pass == 0) != content) continue;
                String title = text(node, "name", "headline");
                String description = text(node, "description");
                String image = imageOf(node);
                if (title.isEmpty() && description.isEmpty() && image.isEmpty()) continue;
                return new LinkPreview(url, clean(title), clean(description), resolve(url, image), publisherOf(node));
            }
        }
        return null;
    }

    private static void collectNodes(JsonElement element, List<JsonObject> out, int depth) {
        if (element == null || element.isJsonNull() || depth > 6 || out.size() >= 200) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectNodes(child, out, depth + 1);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject node = element.getAsJsonObject();
        if (node.has("name") || node.has("headline")) out.add(node);
        for (JsonElement child : node.asMap().values()) {
            if (child.isJsonObject() || child.isJsonArray()) collectNodes(child, out, depth + 1);
        }
    }

    private static String typeOf(JsonObject node) {
        JsonElement type = node.get("@type");
        if (type == null || type.isJsonNull()) return "";
        if (type.isJsonArray()) {
            JsonArray array = type.getAsJsonArray();
            return array.isEmpty() ? "" : array.get(0).getAsString().toLowerCase(Locale.ROOT);
        }
        return type.getAsString().toLowerCase(Locale.ROOT);
    }

    private static String publisherOf(JsonObject node) {
        for (String key : new String[]{"publisher", "author", "creator"}) {
            JsonElement value = node.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (value.isJsonObject()) {
                String name = text(value.getAsJsonObject(), "name");
                if (!name.isEmpty()) return name;
            }
            if (value.isJsonArray()) {
                for (JsonElement child : value.getAsJsonArray()) {
                    if (child.isJsonObject()) {
                        String name = text(child.getAsJsonObject(), "name");
                        if (!name.isEmpty()) return name;
                    }
                }
            }
            if (value.isJsonPrimitive()) return value.getAsString();
        }
        return "";
    }

    /** An image field may be a string, a list, or an object carrying its own url. */
    private static String imageOf(JsonObject node) {
        for (String key : new String[]{"image", "thumbnailUrl", "thumbnail"}) {
            JsonElement value = node.get(key);
            if (value == null || value.isJsonNull()) continue;
            if (value.isJsonPrimitive()) return value.getAsString();
            if (value.isJsonArray()) {
                for (JsonElement child : value.getAsJsonArray()) {
                    String found = imageElement(child);
                    if (!found.isEmpty()) return found;
                }
            }
            String found = imageElement(value);
            if (!found.isEmpty()) return found;
        }
        return "";
    }

    private static String imageElement(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonObject()) return text(value.getAsJsonObject(), "url", "contentUrl");
        return "";
    }

    private static String text(JsonObject node, String... keys) {
        for (String key : keys) {
            JsonElement value = node.get(key);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) continue;
            String text = value.getAsString().trim();
            if (!text.isEmpty()) return text;
        }
        return "";
    }

    /**
     * OpenGraph, Twitter cards, itemprop and plain meta. Sites pick whichever they prefer:
     * Bilibili ships itemprop, YouTube and most news sites ship og.
     */
    static LinkPreview metaCard(String url, String html) {
        String ogTitle = "", ogDescription = "", ogImage = "", ogSite = "";
        String twTitle = "", twDescription = "", twImage = "";
        String itempropTitle = "", itempropDescription = "", itempropImage = "";
        String stdTitle = "", stdDescription = "", stdSite = "";
        Matcher matcher = META.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group(1);
            String content = attr(tag, "content");
            if (content.isEmpty()) continue;
            String itemprop = attr(tag, "itemprop");
            String property = attr(tag, "property");
            String name = attr(tag, "name");
            if (!itemprop.isEmpty()) {
                switch (itemprop.toLowerCase(Locale.ROOT)) {
                    case "name", "headline" -> itempropTitle = fill(itempropTitle, content);
                    case "description" -> itempropDescription = fill(itempropDescription, content);
                    case "image", "thumbnailurl" -> itempropImage = fill(itempropImage, content);
                    default -> {
                    }
                }
                continue;
            }
            if (!property.isEmpty()) {
                switch (property.toLowerCase(Locale.ROOT)) {
                    case "og:title" -> ogTitle = fill(ogTitle, content);
                    case "og:description" -> ogDescription = fill(ogDescription, content);
                    case "og:image", "og:image:url", "og:image:secure_url" -> ogImage = fill(ogImage, content);
                    case "og:site_name" -> ogSite = fill(ogSite, content);
                    case "twitter:title" -> twTitle = fill(twTitle, content);
                    case "twitter:description" -> twDescription = fill(twDescription, content);
                    case "twitter:image", "twitter:image:src" -> twImage = fill(twImage, content);
                    default -> {
                    }
                }
                continue;
            }
            switch (name.toLowerCase(Locale.ROOT)) {
                case "title" -> stdTitle = fill(stdTitle, content);
                case "description" -> stdDescription = fill(stdDescription, content);
                case "application-name", "apple-mobile-web-app-title" -> stdSite = fill(stdSite, content);
                case "twitter:title" -> twTitle = fill(twTitle, content);
                case "twitter:description" -> twDescription = fill(twDescription, content);
                case "twitter:image", "twitter:image:src" -> twImage = fill(twImage, content);
                default -> {
                }
            }
        }
        String title = first(ogTitle, itempropTitle, twTitle, stdTitle);
        String description = first(ogDescription, itempropDescription, twDescription, stdDescription);
        String image = first(ogImage, itempropImage, twImage);
        String site = first(ogSite, stdSite);
        if (title.isEmpty() && description.isEmpty() && image.isEmpty() && site.isEmpty()) return null;
        return new LinkPreview(url, clean(title), clean(description), resolve(url, image), site);
    }

    /** oEmbed is a standard endpoint several big sites advertise in their head. */
    private static LinkPreview oembedCard(String url, String html) {
        String href = "";
        Matcher matcher = OEMBED.matcher(html);
        while (matcher.find()) {
            href = attr(matcher.group(), "href");
            if (!href.isEmpty()) break;
        }
        if (href.isEmpty()) return null;
        String endpoint = resolve(url, href);
        if (!allowed(endpoint)) return null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", UA)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            String json;
            try (InputStream in = response.body()) {
                json = new String(readLimited(in, MAX_JSON), StandardCharsets.UTF_8);
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String title = text(root, "title");
            String author = text(root, "author_name");
            String provider = text(root, "provider_name");
            String thumbnail = text(root, "thumbnail_url");
            if (title.isEmpty() && thumbnail.isEmpty()) return null;
            String site = !author.isEmpty() ? author + " · " + provider : provider;
            return new LinkPreview(url, clean(title), clean(text(root, "description")),
                    resolve(url, thumbnail), site);
        } catch (Exception e) {
            return null;
        }
    }

    /** Last resort before the URL-only card: the first image that is not an icon or a spacer. */
    private static String firstImage(String url, String html) {
        Matcher matcher = IMG.matcher(html);
        while (matcher.find()) {
            String tag = matcher.group(1);
            String src = attr(tag, "src");
            if (src.isEmpty()) src = attr(tag, "data-src");
            if (src.isEmpty()) continue;
            String lower = src.toLowerCase(Locale.ROOT);
            if (lower.startsWith("data:") || lower.endsWith(".svg")) continue;
            if (lower.contains("sprite") || lower.contains("blank.") || lower.contains("pixel.")
                    || lower.contains("spacer") || lower.contains("logo")) continue;
            String absolute = resolve(url, src);
            if (!absolute.isEmpty()) return absolute;
        }
        return "";
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

    /** First attribute with the given name inside a tag. */
    private static String attr(String tag, String name) {
        Matcher matcher = ATTR.matcher(tag);
        while (matcher.find()) {
            if (name.equalsIgnoreCase(matcher.group(1))) {
                String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                return value == null ? "" : value;
            }
        }
        return "";
    }

    private static String fill(String current, String value) {
        return current == null || current.isEmpty() ? value : current;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static String clean(String raw) {
        String text = raw.replaceAll("(?is)<[^>]*>", " ").trim();
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        return text.length() > 300 ? text.substring(0, 300) : text;
    }

    /** Resolves a possibly relative address; protocol relative ones get the page's scheme. */
    private static String resolve(String base, String target) {
        if (target == null || target.isEmpty()) return "";
        try {
            String resolved = URI.create(base).resolve(target).toString();
            if (resolved.startsWith("//")) return "https:" + resolved;
            return resolved;
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
                    .header("User-Agent", UA)
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
