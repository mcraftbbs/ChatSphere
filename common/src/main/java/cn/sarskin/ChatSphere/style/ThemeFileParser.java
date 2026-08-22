package cn.sarskin.ChatSphere.style;

import cn.sarskin.ChatSphere.style.ThemeSpec.AnimSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict white-listed .ctheme parser; any deviation rejects the whole file with a line-numbered ThemeParseException. Nothing is executed. */
public final class ThemeFileParser {
    private ThemeFileParser() {}

    public static final class ThemeParseException extends Exception {
        public ThemeParseException(String message) { super(message); }
        public ThemeParseException(String message, int line) { super(message + " (line " + line + ")"); }
    }

    private static final String COLOR_RE = "#[0-9A-Fa-f]{3}|#[0-9A-Fa-f]{6}|#[0-9A-Fa-f]{8}";
    private static final String HEX_NO_HASH_RE = "[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}";
    private static final String NUMBER_RE = "[0-9]+(\\.[0-9]+)?";
    private static final String IDENT_RE = "[A-Za-z][A-Za-z0-9-]*";

    private static final class Token {
        enum Kind { IDENT, STRING, NUMBER, COLOR, PUNCT }
        final Kind kind;
        final String text;
        final int line;

        Token(Kind kind, String text, int line) {
            this.kind = kind;
            this.text = text;
            this.line = line;
        }
    }

    public static ThemeSpec parse(String text) throws ThemeParseException {
        if (text == null) throw new ThemeParseException("empty theme");
        if (text.startsWith("\uFEFF")) text = text.substring(1); // strip UTF-8 BOM
        if (text.length() > ThemeValidator.MAX_FILE_BYTES)
            throw new ThemeParseException("file too large (max " + ThemeValidator.MAX_FILE_BYTES + " bytes)");

        List<Token> tokens = tokenize(text);
        ThemeSpec spec = new ThemeSpec();
        int pos = 0;

        // optional format magic: CS1 ;
        if (pos + 1 < tokens.size() && isIdent(tokens.get(pos), "CS1")
                && tokens.get(pos + 1).kind == Token.Kind.PUNCT && ";".equals(tokens.get(pos + 1).text)) {
            pos += 2;
        }

        if (pos < tokens.size() && isIdent(tokens.get(pos), "theme")) {
            pos++;
            if (pos >= tokens.size() || tokens.get(pos).kind != Token.Kind.STRING)
                throw new ThemeParseException("expected theme name after 'theme'", lineOf(tokens, pos));
            spec.name = tokens.get(pos).text;
            pos++;
            if (pos >= tokens.size() || !isIdent(tokens.get(pos), "version"))
                throw new ThemeParseException("expected 'version' after theme name", lineOf(tokens, pos));
            pos++;
            if (pos >= tokens.size() || tokens.get(pos).kind != Token.Kind.NUMBER)
                throw new ThemeParseException("expected version number", lineOf(tokens, pos));
            pos++;
            expectPunct(tokens, pos, ";");
            pos++;
        }

        int blocks = 0;
        Set<String> seenBlocks = new HashSet<>();
        while (pos < tokens.size()) {
            if (pos + 1 >= tokens.size() || tokens.get(pos).kind != Token.Kind.IDENT
                    || tokens.get(pos + 1).kind != Token.Kind.PUNCT || !"{".equals(tokens.get(pos + 1).text))
                throw new ThemeParseException("expected block '{' after block name", lineOf(tokens, pos));

            String block = tokens.get(pos).text;
            int blockLine = tokens.get(pos).line;
            if (!ThemeValidator.ALLOWED_BLOCKS.contains(block))
                throw new ThemeParseException("unknown block '" + block + "'", blockLine);
            if (!seenBlocks.add(block))
                throw new ThemeParseException("duplicate block '" + block + "'", blockLine);
            if (blocks >= ThemeValidator.MAX_BLOCKS)
                throw new ThemeParseException("too many blocks (max " + ThemeValidator.MAX_BLOCKS + ")");
            pos += 2;

            pos = parseProps(spec, block, tokens, pos);
            expectPunct(tokens, pos, "}");
            pos++;
            blocks++;
        }

        return spec;
    }

    private static int parseProps(ThemeSpec spec, String block, List<Token> tokens, int pos) throws ThemeParseException {
        int props = 0;
        Set<String> seen = new HashSet<>();
        while (pos < tokens.size()) {
            Token t = tokens.get(pos);
            if (t.kind == Token.Kind.PUNCT && "}".equals(t.text)) return pos;
            if (t.kind != Token.Kind.IDENT)
                throw new ThemeParseException("expected property name", t.line);
            if (props >= ThemeValidator.MAX_PROPS)
                throw new ThemeParseException("too many properties (max " + ThemeValidator.MAX_PROPS + ")");

            String camel = ThemeValidator.normalize(t.text);
            String rawName = t.text;
            int propLine = t.line;
            if (!seen.add(camel))
                throw new ThemeParseException("duplicate property '" + rawName + "'", propLine);
            pos++;
            expectPunct(tokens, pos, ":");
            pos++;
            if (pos >= tokens.size())
                throw new ThemeParseException("expected value after '" + rawName + "'", propLine);

            applyValue(spec, block, camel, rawName, tokens, pos);
            pos = skipValueTokens(tokens, pos);
            if (pos < tokens.size() && tokens.get(pos).kind == Token.Kind.PUNCT && ";".equals(tokens.get(pos).text))
                pos++;
            props++;
        }
        throw new ThemeParseException("unterminated block '" + block + "'", lineOf(tokens, pos));
    }

    private static int skipValueTokens(List<Token> tokens, int pos) {
        while (pos < tokens.size()) {
            Token t = tokens.get(pos);
            boolean percentUnit = t.kind == Token.Kind.PUNCT && "%".equals(t.text);
            if (t.kind != Token.Kind.PUNCT || percentUnit) {
                pos++;
                continue;
            }
            break;
        }
        return pos;
    }

    private static void applyValue(ThemeSpec spec, String block, String camel, String rawName,
                                   List<Token> tokens, int pos) throws ThemeParseException {
        switch (block) {
            case "dark":
            case "light": {
                if (!ThemeValidator.isColorKey(camel))
                    throw new ThemeParseException("unknown color property '" + rawName + "'", tokens.get(pos).line);
                Token v = tokens.get(pos);
                if (v.kind != Token.Kind.COLOR)
                    throw new ThemeParseException("expected color value for '" + rawName + "'", v.line);
                (block.equals("dark") ? spec.dark : spec.light).put(camel, ThemeValidator.parseColorHex(v.text));
                return;
            }
            case "styles": {
                if (!ThemeValidator.isStyleKey(camel))
                    throw new ThemeParseException("unknown style property '" + rawName + "'", tokens.get(pos).line);
                ThemeValidator.PropDef def = ThemeValidator.STYLE_PROPS.get(camel);
                if (def.type() == ThemeValidator.ValType.ENUM) {
                    spec.styles.put(camel, parseEnum(tokens, pos, rawName, camel, def));
                } else if (def.type() == ThemeValidator.ValType.COLOR) {
                    Token v = tokens.get(pos);
                    if (v.kind != Token.Kind.COLOR)
                        throw new ThemeParseException("expected color value for '" + rawName + "'", v.line);
                    spec.styles.put(camel, ThemeValidator.parseColorHex(v.text));
                } else {
                    spec.styles.put(camel, parseNumber(tokens, pos, rawName, def));
                }
                return;
            }
            case "animations": {
                if (!ThemeValidator.isAnimKey(camel))
                    throw new ThemeParseException("unknown animation property '" + rawName + "'", tokens.get(pos).line);
                ThemeValidator.PropDef def = ThemeValidator.ANIM_PROPS.get(camel);
                spec.animations.put(camel, parseAnim(tokens, pos, rawName, def));
                return;
            }
            default:
                throw new ThemeParseException("unreachable block", tokens.get(pos).line);
        }
    }

    private static int parseEnum(List<Token> tokens, int pos, String rawName, String camel, ThemeValidator.PropDef def)
            throws ThemeParseException {
        Token v = tokens.get(pos);
        if (v.kind != Token.Kind.IDENT || !def.enums().contains(v.text))
            throw new ThemeParseException("invalid value for '" + rawName + "' (allowed: "
                    + String.join(", ", def.enums()) + ")", v.line);
        return ThemeValidator.enumIndex(camel, v.text);
    }

    private static int parseNumber(List<Token> tokens, int pos, String rawName, ThemeValidator.PropDef def)
            throws ThemeParseException {
        Token v = tokens.get(pos);
        if (v.kind != Token.Kind.NUMBER)
            throw new ThemeParseException("expected number for '" + rawName + "'", v.line);
        String s = v.text;
        if (pos + 1 < tokens.size()) {
            Token u = tokens.get(pos + 1);
            boolean ok = (u.kind == Token.Kind.IDENT && ("px".equals(u.text) || "ms".equals(u.text)))
                    || (u.kind == Token.Kind.PUNCT && "%".equals(u.text));
            if (!ok && u.kind == Token.Kind.PUNCT && (";".equals(u.text) || "}".equals(u.text))) {
                // no unit
            } else if (!ok) {
                throw new ThemeParseException("unknown unit '" + u.text + "' after '" + rawName + "'", u.line);
            }
        }
        double d = Double.parseDouble(s);
        int val;
        if (def.type() == ThemeValidator.ValType.PERCENT) {
            val = (int) Math.round(d);
            if (val < def.min() || val > def.max())
                throw new ThemeParseException("'" + rawName + "' out of range " + def.min() + "-" + def.max(), v.line);
        } else {
            val = (int) d;
            if (val < def.min() || val > def.max())
                throw new ThemeParseException("'" + rawName + "' out of range " + def.min() + "-" + def.max(), v.line);
        }
        return val;
    }

    private static AnimSpec parseAnim(List<Token> tokens, int pos, String rawName, ThemeValidator.PropDef def)
            throws ThemeParseException {
        Token v = tokens.get(pos);
        if (v.kind == Token.Kind.IDENT && "none".equals(v.text)) {
            return AnimSpec.NONE;
        }
        if (v.kind != Token.Kind.NUMBER)
            throw new ThemeParseException("expected duration (e.g. '120ms') or 'none' for '" + rawName + "'", v.line);
        String s = v.text;
        if (pos + 1 >= tokens.size() || tokens.get(pos + 1).kind != Token.Kind.IDENT
                || !"ms".equals(tokens.get(pos + 1).text))
            throw new ThemeParseException("expected 'ms' unit after duration for '" + rawName + "'", v.line);
        int dur = (int) Double.parseDouble(s);
        if (dur < def.min() || dur > def.max())
            throw new ThemeParseException("'" + rawName + "' duration out of range " + def.min() + "-" + def.max(), v.line);
        if (pos + 2 >= tokens.size() || tokens.get(pos + 2).kind != Token.Kind.IDENT
                || !ThemeValidator.ALLOWED_EASINGS.contains(tokens.get(pos + 2).text))
            throw new ThemeParseException("unknown easing for '" + rawName + "'", lineOf(tokens, pos + 2));
        return new AnimSpec(dur, tokens.get(pos + 2).text);
    }

    private static List<Token> tokenize(String text) throws ThemeParseException {
        List<Token> out = new ArrayList<>();
        int i = 0, n = text.length(), line = 1;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c)) { i++; continue; }

            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) throw new ThemeParseException("unterminated comment", line);
                for (int j = i; j < end; j++) if (text.charAt(j) == '\n') line++;
                i = end + 2;
                continue;
            }

            if (c == '"') {
                int end = text.indexOf('"', i + 1);
                if (end < 0) throw new ThemeParseException("unterminated string", line);
                out.add(new Token(Token.Kind.STRING, text.substring(i + 1, end), line));
                i = end + 1;
                continue;
            }

            if (c == '#') {
                int j = i + 1;
                while (j < n && isHex(text.charAt(j))) j++;
                String h = text.substring(i, j);
                if (!h.matches(COLOR_RE))
                    throw new ThemeParseException("invalid color '" + h + "'", line);
                out.add(new Token(Token.Kind.COLOR, h, line));
                i = j;
                continue;
            }

            if (Character.isDigit(c) || c == '.') {
                int j = i;
                while (j < n && (Character.isDigit(text.charAt(j)) || text.charAt(j) == '.')) j++;
                String num = text.substring(i, j);
                if (num.matches(HEX_NO_HASH_RE)) {
                    // legacy themes wrote colors without the '#' prefix
                    out.add(new Token(Token.Kind.COLOR, num, line));
                    i = j;
                    continue;
                }
                if (!num.matches(NUMBER_RE))
                    throw new ThemeParseException("invalid number '" + num + "'", line);
                out.add(new Token(Token.Kind.NUMBER, num, line));
                i = j;
                continue;
            }

            if (Character.isLetter(c)) {
                int j = i;
                while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '-')) j++;
                String ident = text.substring(i, j);
                if (ident.matches(HEX_NO_HASH_RE)) {
                    // no-'#' hex starting with a letter (e.g. FF000000)
                    out.add(new Token(Token.Kind.COLOR, ident, line));
                    i = j;
                    continue;
                }
                if (!ident.matches(IDENT_RE))
                    throw new ThemeParseException("invalid identifier '" + ident + "'", line);
                out.add(new Token(Token.Kind.IDENT, ident, line));
                i = j;
                continue;
            }

            if (c == '{' || c == '}' || c == ';' || c == ':' || c == '%') {
                out.add(new Token(Token.Kind.PUNCT, String.valueOf(c), line));
                i++;
                continue;
            }

            throw new ThemeParseException("unexpected character '" + c + "'", line);
        }
        return out;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isIdent(Token t, String s) {
        return t.kind == Token.Kind.IDENT && s.equals(t.text);
    }

    private static void expectPunct(List<Token> tokens, int pos, String p) throws ThemeParseException {
        if (pos >= tokens.size() || tokens.get(pos).kind != Token.Kind.PUNCT || !p.equals(tokens.get(pos).text))
            throw new ThemeParseException("expected '" + p + "'", lineOf(tokens, pos));
    }

    private static int lineOf(List<Token> tokens, int pos) {
        return pos < tokens.size() ? tokens.get(pos).line : 0;
    }
}
