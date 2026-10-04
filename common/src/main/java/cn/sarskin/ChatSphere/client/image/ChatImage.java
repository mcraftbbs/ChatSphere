package cn.sarskin.ChatSphere.client.image;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Chat image tokens: {@code [[img:id:WxH]]} for server images, {@code local} id for singleplayer. */
public final class ChatImage {
    public static final String LOCAL = "local";
    public static final Pattern TOKEN = Pattern.compile("\\[\\[img:([A-Za-z0-9_]{1,32}):(\\d{1,5})x(\\d{1,5})]]");

    public record Token(String id, int width, int height) {}

    private ChatImage() {}

    /** Tokens of one message, in text order. */
    public static List<Token> tokens(String text) {
        if (text == null || text.isEmpty()) return List.of();
        List<Token> out = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            try {
                out.add(new Token(matcher.group(1), Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3))));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static String token(String id, int width, int height) {
        return "[[img:" + id + ":" + width + "x" + height + "]]";
    }

    public static boolean hasToken(String text) {
        return text != null && TOKEN.matcher(text).find();
    }

    /** Replaces tokens with a short label; used by the HUD and search results. */
    public static String mapTokens(String text, String label) {
        if (text == null || text.isEmpty()) return text;
        return TOKEN.matcher(text).replaceAll(Matcher.quoteReplacement(label));
    }
}
