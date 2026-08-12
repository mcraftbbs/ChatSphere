package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Lightweight whitelist-based markup parser (jsoup Safelist philosophy; self-contained, zero dependencies).
 *
 * <p>Supported markup (HTML-style, rendered through Minecraft's native Component/Style system):
 * <ul>
 *   <li>[b]x[/b] bold, [i] italic, [u] underline, [s] strikethrough, [o] obfuscated</li>
 *   <li>[color=#RRGGBB]x[/color], [color=red]x[/color] (named colors via ChatFormatting)</li>
 *   <li>[gradient=#AABBCC,#DDEEFF,...]x[/gradient] per-character color interpolation</li>
 *   <li>[code]x[/code] gray monospace-ish text</li>
 *   <li>[url=https://x]label[/url] clickable link; [url]https://x[/url] opens the inner text</li>
 *   <li>\[ escape: typing \[b] shows the literal text</li>
 * </ul>
 * Everything else stays literal. Unclosed/mismatched tags are kept as literal text.
 * Safe by design: only whitelisted tags are interpreted, URLs are sanitized, depth and length are capped.
 */
public final class RichTextParser {

    private RichTextParser() {}

    private static final int MAX_DEPTH = 8;
    private static final int MAX_CHARS = 512;
    private static final int MAX_GRADIENT_STOPS = 16;
    private static final int MAX_TOKENS = 256;

    private static final Pattern TAG_PATTERN = Pattern.compile(
            "\\\\\\[([a-zA-Z]+)(?:=([^\\]]*))?\\]|\\[([a-zA-Z]+)(?:=([^\\]]*))?\\]|\\[/\\s*([a-zA-Z]+)\\s*\\]");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://|www\\.)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");
    private static final Pattern HEX6 = Pattern.compile("#?([0-9a-fA-F]{6})");
    private static final Pattern HEX3 = Pattern.compile("#?([0-9a-fA-F]{3})");
    private static final Pattern GRADIENT = Pattern.compile("#?([0-9a-fA-F]{6})");

    private record Part(boolean isTag, String name, String arg, String text) {}

    private static final class Frame {
        final String tag;
        final Style style;
        final List<Integer> gradient; // null unless gradient frame
        final String url;             // null unless url frame; "" means "use inner text as url"

        Frame(String tag, Style style, List<Integer> gradient, String url) {
            this.tag = tag;
            this.style = style;
            this.gradient = gradient;
            this.url = url;
        }
    }

    public static boolean containsMarkup(String text) {
        return text != null && (text.contains("[") || text.contains("]"));
    }

    /** True if the text contains a linkable bare URL. */
    public static boolean containsUrl(String text) {
        return text != null && URL_PATTERN.matcher(text).find();
    }

    public static MutableComponent parse(String text) {
        return parse(text, Style.EMPTY);
    }

    public static MutableComponent parse(String text, Style baseStyle) {
        if (text == null || text.isEmpty()) return Component.literal("");
        if (!containsMarkup(text)) {
            MutableComponent root = Component.literal("");
            appendLinkified(root, text, baseStyle);
            return root;
        }
        List<Part> parts = tokenize(text);
        MutableComponent root = Component.literal("");
        if (parts.isEmpty() || parts.size() == 1 && !parts.get(0).isTag()) {
            String raw = parts.isEmpty() ? text : parts.get(0).text();
            return EmojiRegistry.toComponent(EmojiRegistry.replaceShortcodes(raw)).withStyle(baseStyle);
        }
        parseTokens(parts, baseStyle, root);
        return root;
    }

    private static List<Part> tokenize(String text) {
        List<Part> parts = new ArrayList<>();
        Matcher m = TAG_PATTERN.matcher(text);
        int last = 0;
        while (m.find() && parts.size() < MAX_TOKENS) {
            if (m.start() > last) {
                parts.add(new Part(false, null, null, text.substring(last, m.start())));
            }
            if (m.group(1) != null) {
                parts.add(new Part(false, null, null,
                        "[" + m.group(1) + (m.group(2) != null ? "=" + m.group(2) + "]" : "]")));
            } else if (m.group(3) != null) {
                parts.add(new Part(true, m.group(3).toLowerCase(), m.group(4), null));
            } else if (m.group(5) != null) {
                parts.add(new Part(true, "/" + m.group(5).toLowerCase(), null, null));
            }
            last = m.end();
        }
        if (last < text.length()) {
            parts.add(new Part(false, null, null, text.substring(last)));
        }
        return parts;
    }

    private static void parseTokens(List<Part> parts, Style baseStyle, MutableComponent root) {
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame("", baseStyle, null, null));
        StringBuilder buf = new StringBuilder();

        for (Part p : parts) {
            if (!p.isTag()) {
                buf.append(p.text());
                if (buf.length() > MAX_CHARS) flush(buf, stack.peek(), root);
                continue;
            }
            flush(buf, stack.peek(), root);
            if (p.name().startsWith("/")) {
                String closing = p.name().substring(1);
                Frame top = stack.peek();
                if (top != stack.getFirst() && top.tag.equals(closing)) {
                    stack.pop();
                }
            } else {
                if (stack.size() - 1 >= MAX_DEPTH) {
                    buf.append("[").append(p.name()).append(p.arg() != null ? "=" + p.arg() : "").append("]");
                    continue;
                }
                Frame frame = buildFrame(stack.peek(), p.name(), p.arg());
                if (frame != null) {
                    stack.push(frame);
                } else {
                    buf.append("[").append(p.name()).append(p.arg() != null ? "=" + p.arg() : "").append("]");
                }
            }
        }
        flush(buf, stack.peek(), root);
    }

    private static Frame buildFrame(Frame parent, String tag, String arg) {
        Style s = parent.style;
        switch (tag) {
            case "b": return new Frame("b", s.withBold(true), null, null);
            case "i": return new Frame("i", s.withItalic(true), null, null);
            case "u": return new Frame("u", s.withUnderlined(true), null, null);
            case "s": return new Frame("s", s.withStrikethrough(true), null, null);
            case "o": return new Frame("o", s.withObfuscated(true), null, null);
            case "color": {
                TextColor color = parseColor(arg);
                return color != null ? new Frame("color", s.withColor(color), null, null) : null;
            }
            case "gradient": {
                List<Integer> stops = parseGradient(arg);
                return stops != null ? new Frame("gradient", s, stops, null) : null;
            }
            case "code": return new Frame("code", s.withColor(ChatFormatting.GRAY), null, null);
            case "url": {
                String u = arg != null ? sanitizeUrl(arg) : "";
                return new Frame("url", s.withUnderlined(true), null, u);
            }
            default: return null;
        }
    }

    private static void flush(StringBuilder buf, Frame frame, MutableComponent root) {
        if (buf.length() == 0) return;
        String s = buf.toString();
        if (frame.gradient != null) {
            appendGradient(root, s, frame.gradient, frame.style);
        } else {
            Style style = frame.style;
            if (frame.url != null) {
                String url = frame.url.isEmpty() ? s.trim() : frame.url;
                url = sanitizeUrl(url);
                style = style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(url)));
            }
            appendLinkified(root, s, style);
        }
        buf.setLength(0);
    }

    /** Split plain text on bare URLs and make them clickable. */
    private static void appendLinkified(MutableComponent root, String s, Style style) {
        Matcher m = URL_PATTERN.matcher(s);
        int last = 0;
        while (m.find()) {
            String g = m.group();
            int end = g.length();
            while (end > 0 && ".,;:!?)]}".indexOf(g.charAt(end - 1)) >= 0) end--;
            String url = g.substring(0, end);
            String tail = g.substring(end);
            if (m.start() > last) root.append(toEmoji(s.substring(last, m.start()), style));
            if (!url.isEmpty()) {
                if (isUrlAllowed(url)) {
                    String openUrl = url.startsWith("www.") ? "https://" + url : url;
                    Style linkStyle = style.withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, openUrl))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(openUrl)));
                    root.append(toEmoji(url, linkStyle));
                } else {
                    root.append(toEmoji(url, style));
                }
            }
            root.append(toEmoji(tail, style));
            last = m.end();
        }
        if (last < s.length()) root.append(toEmoji(s.substring(last), style));
    }

    private static MutableComponent toEmoji(String s, Style style) {
        return EmojiRegistry.toComponent(EmojiRegistry.replaceShortcodes(s)).withStyle(style);
    }

    /** null/empty filter allows everything; non-empty lists act as a whitelist. */
    private static boolean isUrlAllowed(String url) {
        List<String> patterns;
        try {
            patterns = ModClientConfig.CONFIG.urlLinkFilter.get();
        } catch (RuntimeException e) {
            return true;
        }
        if (patterns == null || patterns.isEmpty()) return true;
        for (String p : patterns) {
            if (p == null || p.isEmpty()) continue;
            try {
                if (Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(url).find()) return true;
            } catch (PatternSyntaxException ignored) {
            }
        }
        return false;
    }

    private static void appendGradient(MutableComponent root, String s, List<Integer> stops, Style base) {
        int total = s.codePointCount(0, s.length());
        int idx = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int cc = Character.charCount(cp);
            String ch = s.substring(i, i + cc);
            double t = total <= 1 ? 0.0 : (double) idx / (total - 1);
            int color = interpolate(stops, t);
            root.append(EmojiRegistry.toComponent(ch).withStyle(base.withColor(TextColor.fromRgb(color))));
            i += cc;
            idx++;
        }
    }

    private static int interpolate(List<Integer> stops, double t) {
        if (stops.size() == 2) {
            return lerp(stops.get(0), stops.get(1), t);
        }
        double seg = t * (stops.size() - 1);
        int idx = Math.min((int) seg, stops.size() - 2);
        double local = seg - idx;
        return lerp(stops.get(idx), stops.get(idx + 1), local);
    }

    private static int lerp(int a, int b, double t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) Math.round(ar + (br - ar) * t);
        int g = (int) Math.round(ag + (bg - ag) * t);
        int bl = (int) Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static TextColor parseColor(String arg) {
        if (arg == null) return null;
        String a = arg.trim();
        Matcher m6 = HEX6.matcher(a);
        if (m6.matches()) {
            return TextColor.fromRgb(Integer.parseInt(m6.group(1), 16));
        }
        Matcher m3 = HEX3.matcher(a);
        if (m3.matches()) {
            String g = m3.group(1);
            int r = Integer.parseInt(g.substring(0, 1) + g.substring(0, 1), 16);
            int gr = Integer.parseInt(g.substring(1, 2) + g.substring(1, 2), 16);
            int b = Integer.parseInt(g.substring(2, 3) + g.substring(2, 3), 16);
            return TextColor.fromRgb((r << 16) | (gr << 8) | b);
        }
        ChatFormatting fmt = ChatFormatting.getByName(a.toLowerCase());
        if (fmt != null && fmt.isColor()) {
            return TextColor.fromLegacyFormat(fmt);
        }
        return null;
    }

    private static List<Integer> parseGradient(String arg) {
        if (arg == null) return null;
        List<Integer> stops = new ArrayList<>();
        Matcher m = GRADIENT.matcher(arg);
        while (m.find() && stops.size() < MAX_GRADIENT_STOPS) {
            stops.add(Integer.parseInt(m.group(1), 16));
        }
        return stops.size() >= 2 ? stops : null;
    }

    private static String sanitizeUrl(String url) {
        String u = url == null ? "" : url.trim();
        String lower = u.toLowerCase();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")) return "#";
        if (lower.startsWith("http://") || lower.startsWith("https://")) return u;
        if (u.isEmpty()) return "#";
        return "https://" + u;
    }
}
