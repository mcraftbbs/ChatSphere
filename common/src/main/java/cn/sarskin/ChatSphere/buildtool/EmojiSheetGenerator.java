package cn.sarskin.ChatSphere.buildtool;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class EmojiSheetGenerator {
    static final String CDN = "https://cdn.jsdelivr.net/gh/twitter/twemoji@v14.0.2/assets/72x72/";
    static final int CELL = 16, COLS = 16, PUA_START = 0xE000;
    static final File PNG_DIR = new File("src/main/resources/assets/chatsphere/textures/font");
    static final File JSON_DIR = new File("src/main/resources/assets/chatsphere/font");
    static final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL).build();

    record EmojiDef(String shortcode, String unicode) {}

    public static void main(String[] args) throws Exception {
        PNG_DIR.mkdirs();
        JSON_DIR.mkdirs();
        List<EmojiDef> list = buildEmojiList();
        int total = list.size(), sheets = (total + 255) / 256;
        BufferedImage[] imgs = new BufferedImage[total];
        int cdn = 0, awt = 0, fail = 0;

        for (int i = 0; i < total; i++) {
            String url = CDN + toHexFileName(list.get(i).unicode);
            imgs[i] = null;
            try {
                byte[] body = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()).body();
                BufferedImage orig = ImageIO.read(new ByteArrayInputStream(body));
                if (orig != null) {
                    imgs[i] = resize(orig, CELL, CELL);
                    cdn++;
                }
            } catch (Exception ignored) {}
            if (imgs[i] == null) {
                try {
                    imgs[i] = renderAwt(list.get(i).unicode);
                    awt++;
                } catch (Exception e2) {
                    imgs[i] = fallbackCell();
                    fail++;
                }
            }
            if ((i + 1) % 50 == 0 || i == total - 1)
                System.out.println((i + 1) + "/" + total + " cdn=" + cdn + " awt=" + awt + " fail=" + fail);
        }

        for (int s = 0; s < sheets; s++) {
            int first = s * 256, last = Math.min(first + 256, total);
            int rows = (last - first + COLS - 1) / COLS;
            int sheetH = rows * CELL;
            BufferedImage sheet = new BufferedImage(256, sheetH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = sheet.createGraphics();
            for (int i = first; i < last; i++) {
                int local = i - first, col = local % COLS, row = local / COLS;
                g.drawImage(imgs[i], col * CELL, row * CELL, null);
            }
            g.dispose();
            String name = s == 0 ? "emoji.png" : "emoji_" + String.format("%02d", s) + ".png";
            ImageIO.write(sheet, "PNG", new File(PNG_DIR, name));
            System.out.println("Written " + name + " (" + 256 + "x" + sheetH + ")");
        }
        writeFontJson(sheets, total);
        System.out.println("Done! " + total + " emoji, " + sheets + " sheet(s)");
    }

    static void writeFontJson(int sheets, int total) throws Exception {
        StringBuilder sb = new StringBuilder("{\n  \"providers\": [\n");
        for (int s = 0; s < sheets; s++) {
            String name = s == 0 ? "emoji.png" : "emoji_" + String.format("%02d", s) + ".png";
            sb.append("    {\n      \"type\": \"bitmap\",\n");
            sb.append("      \"file\": \"chatsphere:font/").append(name).append("\",\n");
            sb.append("      \"ascent\": 10,\n      \"height\": 13,\n      \"chars\": [\n");
            int first = s * 256, last = Math.min(first + 256, total);
            int rows = (last - first + 15) / 16;
            for (int r = 0; r < rows; r++) {
                sb.append("        \"");
                for (int c = 0; c < 16; c++) {
                    int idx = first + r * 16 + c;
                    sb.append(idx < last ? "\\u" + String.format("%04X", PUA_START + idx) : "\\u0000");
                }
                sb.append("\"").append(r < rows - 1 ? "," : "").append("\n");
            }
            sb.append("      ]\n    }").append(s < sheets - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n}\n");
        try (var w = new OutputStreamWriter(new FileOutputStream(new File(JSON_DIR, "emoji.json")), StandardCharsets.UTF_8)) {
            w.write(sb.toString());
        }
        System.out.println("Written emoji.json");
    }

    static String toHexFileName(String unicode) {
        int[] cps = unicode.codePoints().filter(cp -> cp != 0xFE0F).toArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cps.length; i++) {
            if (i > 0) sb.append('_');
            sb.append(String.format("%x", cps[i]));
        }
        return sb + ".png";
    }

    static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    static BufferedImage renderAwt(String unicode) {
        int sz = 64;
        BufferedImage tmp = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Font font = new Font("Segoe UI Emoji", Font.PLAIN, sz - 4);
        g.setFont(font);
        FontRenderContext frc = g.getFontRenderContext();
        Rectangle2D bounds = font.getStringBounds(unicode, frc);
        int x = (int) Math.round((sz - bounds.getWidth()) / 2 - bounds.getX());
        g.drawString(unicode, x, sz - 6);
        g.dispose();
        return resize(tmp, CELL, CELL);
    }

    static BufferedImage fallbackCell() {
        BufferedImage img = new BufferedImage(CELL, CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 0, 0, 80));
        g.fillRect(1, 1, CELL - 2, CELL - 2);
        g.dispose();
        return img;
    }

    static List<EmojiDef> buildEmojiList() {
        var list = new ArrayList<EmojiDef>();
        list.add(new EmojiDef(":grin:", "\uD83D\uDE01"));
        list.add(new EmojiDef(":smiley:", "\uD83D\uDE03"));
        list.add(new EmojiDef(":smile:", "\uD83D\uDE04"));
        list.add(new EmojiDef(":sweat_smile:", "\uD83D\uDE05"));
        list.add(new EmojiDef(":laughing:", "\uD83D\uDE06"));
        list.add(new EmojiDef(":joy:", "\uD83D\uDE02"));
        list.add(new EmojiDef(":rofl:", "\uD83E\uDD23"));
        list.add(new EmojiDef(":relaxed:", "\u263A\uFE0F"));
        list.add(new EmojiDef(":blush:", "\uD83D\uDE0A"));
        list.add(new EmojiDef(":innocent:", "\uD83D\uDE07"));
        list.add(new EmojiDef(":wink:", "\uD83D\uDE09"));
        list.add(new EmojiDef(":heart_eyes:", "\uD83D\uDE0D"));
        list.add(new EmojiDef(":kissing_heart:", "\uD83D\uDE18"));
        list.add(new EmojiDef(":kissing:", "\uD83D\uDE17"));
        list.add(new EmojiDef(":yum:", "\uD83D\uDE0B"));
        list.add(new EmojiDef(":stuck_out_tongue:", "\uD83D\uDE1B"));
        list.add(new EmojiDef(":stuck_out_tongue_winking_eye:", "\uD83D\uDE1C"));
        list.add(new EmojiDef(":zany:", "\uD83E\uDD2A"));
        list.add(new EmojiDef(":facepalm:", "\uD83E\uDD26"));
        list.add(new EmojiDef(":shrug:", "\uD83E\uDD37"));
        list.add(new EmojiDef(":nerd:", "\uD83E\uDD13"));
        list.add(new EmojiDef(":cool:", "\uD83D\uDE0E"));
        list.add(new EmojiDef(":thinking:", "\uD83E\uDD14"));
        list.add(new EmojiDef(":neutral:", "\uD83D\uDE10"));
        list.add(new EmojiDef(":expressionless:", "\uD83D\uDE11"));
        list.add(new EmojiDef(":no_mouth:", "\uD83D\uDE36"));
        list.add(new EmojiDef(":unamused:", "\uD83D\uDE12"));
        list.add(new EmojiDef(":roll_eyes:", "\uD83D\uDE44"));
        list.add(new EmojiDef(":smirk:", "\uD83D\uDE0F"));
        list.add(new EmojiDef(":persevere:", "\uD83D\uDE23"));
        list.add(new EmojiDef(":disappointed:", "\uD83D\uDE1E"));
        list.add(new EmojiDef(":sweat:", "\uD83D\uDE13"));
        list.add(new EmojiDef(":weary:", "\uD83D\uDE29"));
        list.add(new EmojiDef(":tired:", "\uD83D\uDE2B"));
        list.add(new EmojiDef(":sob:", "\uD83D\uDE2D"));
        list.add(new EmojiDef(":cry:", "\uD83D\uDE22"));
        list.add(new EmojiDef(":anguished:", "\uD83D\uDE27"));
        list.add(new EmojiDef(":fearful:", "\uD83D\uDE28"));
        list.add(new EmojiDef(":worried:", "\uD83D\uDE1F"));
        list.add(new EmojiDef(":flushed:", "\uD83D\uDE33"));
        list.add(new EmojiDef(":astonished:", "\uD83D\uDE32"));
        list.add(new EmojiDef(":scream:", "\uD83D\uDE31"));
        list.add(new EmojiDef(":angry:", "\uD83D\uDE20"));
        list.add(new EmojiDef(":rage:", "\uD83D\uDE21"));
        list.add(new EmojiDef(":triumph:", "\uD83D\uDE24"));
        list.add(new EmojiDef(":sleepy:", "\uD83D\uDE2A"));
        list.add(new EmojiDef(":sleeping:", "\uD83D\uDE34"));
        list.add(new EmojiDef(":dizzy:", "\uD83D\uDE35"));
        list.add(new EmojiDef(":sunglasses:", "\uD83D\uDE0E"));
        list.add(new EmojiDef(":drool:", "\uD83E\uDD24"));
        list.add(new EmojiDef(":woozy:", "\uD83E\uDD74"));
        list.add(new EmojiDef(":exploding_head:", "\uD83E\uDD2F"));
        list.add(new EmojiDef(":cowboy:", "\uD83E\uDD20"));
        list.add(new EmojiDef(":partying:", "\uD83E\uDD73"));
        list.add(new EmojiDef(":pleading:", "\uD83E\uDD7A"));
        list.add(new EmojiDef(":lying:", "\uD83E\uDD25"));
        list.add(new EmojiDef(":shushing:", "\uD83E\uDD2B"));
        list.add(new EmojiDef(":yawning:", "\uD83E\uDD71"));
        list.add(new EmojiDef(":mask:", "\uD83D\uDE37"));
        list.add(new EmojiDef(":thermometer:", "\uD83E\uDD12"));
        list.add(new EmojiDef(":head_bandage:", "\uD83E\uDD15"));
        list.add(new EmojiDef(":sick:", "\uD83E\uDD22"));
        list.add(new EmojiDef(":vomit:", "\uD83E\uDD2E"));
        list.add(new EmojiDef(":sneeze:", "\uD83E\uDD27"));
        list.add(new EmojiDef(":hot:", "\uD83E\uDD75"));
        list.add(new EmojiDef(":cold:", "\uD83E\uDD76"));
        list.add(new EmojiDef(":smiling_imp:", "\uD83D\uDE08"));
        list.add(new EmojiDef(":imp:", "\uD83D\uDC7F"));
        list.add(new EmojiDef(":skull:", "\uD83D\uDC80"));
        list.add(new EmojiDef(":skull_crossbones:", "\u2620\uFE0F"));
        list.add(new EmojiDef(":poop:", "\uD83D\uDCA9"));
        list.add(new EmojiDef(":clown:", "\uD83E\uDD21"));
        list.add(new EmojiDef(":alien:", "\uD83D\uDC7D"));
        list.add(new EmojiDef(":robot:", "\uD83E\uDD16"));
        list.add(new EmojiDef(":ghost:", "\uD83D\uDC7B"));
        list.add(new EmojiDef(":wave:", "\uD83D\uDC4B"));
        list.add(new EmojiDef(":raised_hand:", "\u270B"));
        list.add(new EmojiDef(":ok_hand:", "\uD83D\uDC4C"));
        list.add(new EmojiDef(":v:", "\u270C\uFE0F"));
        list.add(new EmojiDef(":crossed_fingers:", "\uD83E\uDD1E"));
        list.add(new EmojiDef(":love_you:", "\uD83E\uDD1F"));
        list.add(new EmojiDef(":sign_of_horns:", "\uD83E\uDD18"));
        list.add(new EmojiDef(":call_me:", "\uD83E\uDD19"));
        list.add(new EmojiDef(":thumbsup:", "\uD83D\uDC4D"));
        list.add(new EmojiDef(":thumbsdown:", "\uD83D\uDC4E"));
        list.add(new EmojiDef(":fist:", "\u270A"));
        list.add(new EmojiDef(":fist_bump:", "\uD83D\uDC4A"));
        list.add(new EmojiDef(":clap:", "\uD83D\uDC4F"));
        list.add(new EmojiDef(":raised_hands:", "\uD83D\uDE4C"));
        list.add(new EmojiDef(":open_hands:", "\uD83D\uDC50"));
        list.add(new EmojiDef(":handshake:", "\uD83E\uDD1D"));
        list.add(new EmojiDef(":pray:", "\uD83D\uDE4F"));
        list.add(new EmojiDef(":writing_hand:", "\u270D\uFE0F"));
        list.add(new EmojiDef(":nail_care:", "\uD83D\uDC85"));
        list.add(new EmojiDef(":muscle:", "\uD83D\uDCAA"));
        list.add(new EmojiDef(":ear:", "\uD83D\uDC42"));
        list.add(new EmojiDef(":nose:", "\uD83D\uDC43"));
        list.add(new EmojiDef(":eyes:", "\uD83D\uDC40"));
        list.add(new EmojiDef(":eye:", "\uD83D\uDC41\uFE0F"));
        list.add(new EmojiDef(":tongue:", "\uD83D\uDC45"));
        list.add(new EmojiDef(":lips:", "\uD83D\uDC44"));
        list.add(new EmojiDef(":brain:", "\uD83E\uDDE0"));
        list.add(new EmojiDef(":heart:", "\u2764\uFE0F"));
        list.add(new EmojiDef(":orange_heart:", "\uD83E\uDDE1"));
        list.add(new EmojiDef(":yellow_heart:", "\uD83D\uDC9B"));
        list.add(new EmojiDef(":green_heart:", "\uD83D\uDC9A"));
        list.add(new EmojiDef(":blue_heart:", "\uD83D\uDC99"));
        list.add(new EmojiDef(":purple_heart:", "\uD83D\uDC9C"));
        list.add(new EmojiDef(":black_heart:", "\uD83D\uDDA4"));
        list.add(new EmojiDef(":white_heart:", "\uD83E\uDD0D"));
        list.add(new EmojiDef(":broken_heart:", "\uD83D\uDC94"));
        list.add(new EmojiDef(":heart_exclamation:", "\u2763\uFE0F"));
        list.add(new EmojiDef(":two_hearts:", "\uD83D\uDC95"));
        list.add(new EmojiDef(":sparkling_heart:", "\uD83D\uDC96"));
        list.add(new EmojiDef(":heartpulse:", "\uD83D\uDC97"));
        list.add(new EmojiDef(":cupid:", "\uD83D\uDC98"));
        list.add(new EmojiDef(":gift_heart:", "\uD83D\uDC9D"));
        list.add(new EmojiDef(":revolving_hearts:", "\uD83D\uDC9E"));
        list.add(new EmojiDef(":heartbeat:", "\uD83D\uDC93"));
        list.add(new EmojiDef(":kiss:", "\uD83D\uDC8B"));
        list.add(new EmojiDef(":100:", "\uD83D\uDCAF"));
        list.add(new EmojiDef(":fire:", "\uD83D\uDD25"));
        list.add(new EmojiDef(":sparkles:", "\u2728"));
        list.add(new EmojiDef(":star:", "\u2B50"));
        list.add(new EmojiDef(":glowing_star:", "\uD83C\uDF1F"));
        list.add(new EmojiDef(":dizzy_star:", "\uD83D\uDCAB"));
        list.add(new EmojiDef(":boom:", "\uD83D\uDCA5"));
        list.add(new EmojiDef(":exclamation:", "\u2757"));
        list.add(new EmojiDef(":question:", "\u2753"));
        list.add(new EmojiDef(":white_check_mark:", "\u2705"));
        list.add(new EmojiDef(":x:", "\u274C"));
        list.add(new EmojiDef(":zzz:", "\uD83D\uDCA4"));
        list.add(new EmojiDef(":musical_note:", "\uD83C\uDFB5"));
        list.add(new EmojiDef(":notes:", "\uD83C\uDFB6"));
        list.add(new EmojiDef(":radioactive:", "\u2622\uFE0F"));
        list.add(new EmojiDef(":biohazard:", "\u2623\uFE0F"));
        list.add(new EmojiDef(":warning:", "\u26A0\uFE0F"));
        list.add(new EmojiDef(":no_entry:", "\u26D4"));
        list.add(new EmojiDef(":tm:", "\u2122\uFE0F"));
        list.add(new EmojiDef(":copyright:", "\u00A9\uFE0F"));
        list.add(new EmojiDef(":registered:", "\u00AE\uFE0F"));
        list.add(new EmojiDef(":atm:", "\uD83C\uDFE7"));
        list.add(new EmojiDef(":wc:", "\uD83D\uDEBE"));
        list.add(new EmojiDef(":potable_water:", "\uD83D\uDEB0"));
        list.add(new EmojiDef(":restroom:", "\uD83D\uDEBB"));
        list.add(new EmojiDef(":mens:", "\uD83D\uDEB9"));
        list.add(new EmojiDef(":womens:", "\uD83D\uDEBA"));
        list.add(new EmojiDef(":smoking:", "\uD83D\uDEAC"));
        list.add(new EmojiDef(":no_smoking:", "\uD83D\uDEAD"));
        list.add(new EmojiDef(":recycle:", "\u267B\uFE0F"));
        list.add(new EmojiDef(":dog:", "\uD83D\uDC36"));
        list.add(new EmojiDef(":cat:", "\uD83D\uDC31"));
        list.add(new EmojiDef(":mouse_face:", "\uD83D\uDC2D"));
        list.add(new EmojiDef(":hamster:", "\uD83D\uDC39"));
        list.add(new EmojiDef(":rabbit:", "\uD83D\uDC30"));
        list.add(new EmojiDef(":fox:", "\uD83E\uDD8A"));
        list.add(new EmojiDef(":bear:", "\uD83D\uDC3B"));
        list.add(new EmojiDef(":panda:", "\uD83D\uDC3C"));
        list.add(new EmojiDef(":koala:", "\uD83D\uDC28"));
        list.add(new EmojiDef(":tiger:", "\uD83D\uDC2F"));
        list.add(new EmojiDef(":lion:", "\uD83E\uDD81"));
        list.add(new EmojiDef(":cow:", "\uD83D\uDC2E"));
        list.add(new EmojiDef(":pig:", "\uD83D\uDC37"));
        list.add(new EmojiDef(":frog:", "\uD83D\uDC38"));
        list.add(new EmojiDef(":monkey:", "\uD83D\uDC35"));
        list.add(new EmojiDef(":monkey_face:", "\uD83D\uDC12"));
        list.add(new EmojiDef(":chicken:", "\uD83D\uDC14"));
        list.add(new EmojiDef(":penguin:", "\uD83D\uDC27"));
        list.add(new EmojiDef(":bird:", "\uD83D\uDC26"));
        list.add(new EmojiDef(":eagle:", "\uD83E\uDD85"));
        list.add(new EmojiDef(":duck:", "\uD83E\uDD86"));
        list.add(new EmojiDef(":owl:", "\uD83E\uDD89"));
        list.add(new EmojiDef(":bat:", "\uD83E\uDD87"));
        list.add(new EmojiDef(":wolf:", "\uD83D\uDC3A"));
        list.add(new EmojiDef(":horse:", "\uD83D\uDC34"));
        list.add(new EmojiDef(":unicorn:", "\uD83E\uDD84"));
        list.add(new EmojiDef(":bee:", "\uD83D\uDC1D"));
        list.add(new EmojiDef(":bug:", "\uD83D\uDC1B"));
        list.add(new EmojiDef(":butterfly:", "\uD83E\uDD8B"));
        list.add(new EmojiDef(":snail:", "\uD83D\uDC0C"));
        list.add(new EmojiDef(":turtle:", "\uD83D\uDC22"));
        list.add(new EmojiDef(":snake:", "\uD83D\uDC0D"));
        list.add(new EmojiDef(":dragon:", "\uD83D\uDC09"));
        list.add(new EmojiDef(":whale:", "\uD83D\uDC33"));
        list.add(new EmojiDef(":dolphin:", "\uD83D\uDC2C"));
        list.add(new EmojiDef(":fish:", "\uD83D\uDC1F"));
        list.add(new EmojiDef(":octopus:", "\uD83D\uDC19"));
        list.add(new EmojiDef(":crab:", "\uD83E\uDD80"));
        list.add(new EmojiDef(":lizard:", "\uD83E\uDD8E"));
        list.add(new EmojiDef(":cactus:", "\uD83C\uDF35"));
        list.add(new EmojiDef(":cherry_blossom:", "\uD83C\uDF38"));
        list.add(new EmojiDef(":rose:", "\uD83C\uDF39"));
        list.add(new EmojiDef(":hibiscus:", "\uD83C\uDF3A"));
        list.add(new EmojiDef(":sunflower:", "\uD83C\uDF3B"));
        list.add(new EmojiDef(":blossom:", "\uD83C\uDF3C"));
        list.add(new EmojiDef(":herb:", "\uD83C\uDF3F"));
        list.add(new EmojiDef(":four_leaf_clover:", "\uD83C\uDF40"));
        list.add(new EmojiDef(":maple_leaf:", "\uD83C\uDF41"));
        list.add(new EmojiDef(":fallen_leaf:", "\uD83C\uDF42"));
        list.add(new EmojiDef(":mushroom:", "\uD83C\uDF44"));
        list.add(new EmojiDef(":earth:", "\uD83C\uDF0D"));
        list.add(new EmojiDef(":moon:", "\uD83C\uDF19"));
        list.add(new EmojiDef(":sunny:", "\u2600\uFE0F"));
        list.add(new EmojiDef(":rainbow:", "\uD83C\uDF08"));
        list.add(new EmojiDef(":cloud:", "\u2601\uFE0F"));
        list.add(new EmojiDef(":zap:", "\u26A1"));
        list.add(new EmojiDef(":snowflake:", "\u2744\uFE0F"));
        list.add(new EmojiDef(":droplet:", "\uD83D\uDCA7"));
        list.add(new EmojiDef(":ocean:", "\uD83C\uDF0A"));
        list.add(new EmojiDef(":apple:", "\uD83C\uDF4E"));
        list.add(new EmojiDef(":green_apple:", "\uD83C\uDF4F"));
        list.add(new EmojiDef(":banana:", "\uD83C\uDF4C"));
        list.add(new EmojiDef(":grapes:", "\uD83C\uDF47"));
        list.add(new EmojiDef(":watermelon:", "\uD83C\uDF49"));
        list.add(new EmojiDef(":strawberry:", "\uD83C\uDF53"));
        list.add(new EmojiDef(":cherries:", "\uD83C\uDF52"));
        list.add(new EmojiDef(":peach:", "\uD83C\uDF51"));
        list.add(new EmojiDef(":pizza:", "\uD83C\uDF55"));
        list.add(new EmojiDef(":hamburger:", "\uD83C\uDF54"));
        list.add(new EmojiDef(":fries:", "\uD83C\uDF5F"));
        list.add(new EmojiDef(":hotdog:", "\uD83C\uDF2D"));
        list.add(new EmojiDef(":taco:", "\uD83C\uDF2E"));
        list.add(new EmojiDef(":burrito:", "\uD83C\uDF2F"));
        list.add(new EmojiDef(":sushi:", "\uD83C\uDF63"));
        list.add(new EmojiDef(":rice:", "\uD83C\uDF5A"));
        list.add(new EmojiDef(":ramen:", "\uD83C\uDF5C"));
        list.add(new EmojiDef(":spaghetti:", "\uD83C\uDF5D"));
        list.add(new EmojiDef(":bread:", "\uD83C\uDF5E"));
        list.add(new EmojiDef(":cake:", "\uD83C\uDF70"));
        list.add(new EmojiDef(":cookie:", "\uD83C\uDF6A"));
        list.add(new EmojiDef(":chocolate:", "\uD83C\uDF6B"));
        list.add(new EmojiDef(":donut:", "\uD83C\uDF69"));
        list.add(new EmojiDef(":icecream:", "\uD83C\uDF66"));
        list.add(new EmojiDef(":beer:", "\uD83C\uDF7A"));
        list.add(new EmojiDef(":wine:", "\uD83C\uDF77"));
        list.add(new EmojiDef(":cocktail:", "\uD83C\uDF78"));
        list.add(new EmojiDef(":coffee:", "\u2615"));
        list.add(new EmojiDef(":tea:", "\uD83C\uDF75"));
        list.add(new EmojiDef(":sake:", "\uD83C\uDF76"));
        list.add(new EmojiDef(":soccer:", "\u26BD"));
        list.add(new EmojiDef(":basketball:", "\uD83C\uDFC0"));
        list.add(new EmojiDef(":football:", "\uD83C\uDFC8"));
        list.add(new EmojiDef(":baseball:", "\u26BE"));
        list.add(new EmojiDef(":tennis:", "\uD83C\uDFBE"));
        list.add(new EmojiDef(":volleyball:", "\uD83C\uDFD0"));
        list.add(new EmojiDef(":golf:", "\u26F3"));
        list.add(new EmojiDef(":trophy:", "\uD83C\uDFC6"));
        list.add(new EmojiDef(":medal:", "\uD83C\uDFC5"));
        list.add(new EmojiDef(":gold:", "\uD83E\uDD47"));
        list.add(new EmojiDef(":silver:", "\uD83E\uDD48"));
        list.add(new EmojiDef(":bronze:", "\uD83E\uDD49"));
        list.add(new EmojiDef(":game_die:", "\uD83C\uDFB2"));
        list.add(new EmojiDef(":chess:", "\u265F\uFE0F"));
        list.add(new EmojiDef(":art:", "\uD83C\uDFA8"));
        list.add(new EmojiDef(":guitar:", "\uD83C\uDFB8"));
        list.add(new EmojiDef(":trumpet:", "\uD83C\uDFBA"));
        list.add(new EmojiDef(":violin:", "\uD83C\uDFBB"));
        list.add(new EmojiDef(":drum:", "\uD83E\uDD41"));
        list.add(new EmojiDef(":microphone:", "\uD83C\uDFA4"));
        list.add(new EmojiDef(":headphones:", "\uD83C\uDFA7"));
        list.add(new EmojiDef(":ticket:", "\uD83C\uDFAB"));
        list.add(new EmojiDef(":clapper:", "\uD83C\uDFAC"));
        list.add(new EmojiDef(":video_game:", "\uD83C\uDFAE"));
        list.add(new EmojiDef(":dart:", "\uD83C\uDFAF"));
        list.add(new EmojiDef(":slot_machine:", "\uD83C\uDFB0"));
        list.add(new EmojiDef(":8ball:", "\uD83C\uDFB1"));
        list.add(new EmojiDef(":airplane:", "\u2708\uFE0F"));
        list.add(new EmojiDef(":car:", "\uD83D\uDE97"));
        list.add(new EmojiDef(":taxi:", "\uD83D\uDE95"));
        list.add(new EmojiDef(":bus:", "\uD83D\uDE8C"));
        list.add(new EmojiDef(":train:", "\uD83D\uDE86"));
        list.add(new EmojiDef(":rocket:", "\uD83D\uDE80"));
        list.add(new EmojiDef(":satellite:", "\uD83D\uDEF0\uFE0F"));
        list.add(new EmojiDef(":ship:", "\uD83D\uDEA2"));
        list.add(new EmojiDef(":bicycle:", "\uD83D\uDEB2"));
        list.add(new EmojiDef(":motorcycle:", "\uD83C\uDFCD\uFE0F"));
        list.add(new EmojiDef(":house:", "\uD83C\uDFE0"));
        list.add(new EmojiDef(":office:", "\uD83C\uDFE2"));
        list.add(new EmojiDef(":hospital:", "\uD83C\uDFE5"));
        list.add(new EmojiDef(":bank:", "\uD83C\uDFE6"));
        list.add(new EmojiDef(":hotel:", "\uD83C\uDFE8"));
        list.add(new EmojiDef(":church:", "\u26EA"));
        list.add(new EmojiDef(":mosque:", "\uD83D\uDD4C"));
        list.add(new EmojiDef(":castle:", "\uD83C\uDFF0"));
        list.add(new EmojiDef(":japan:", "\uD83D\uDDFE"));
        list.add(new EmojiDef(":mount_fuji:", "\uD83D\uDDFB"));
        list.add(new EmojiDef(":beach:", "\uD83C\uDFD6\uFE0F"));
        list.add(new EmojiDef(":desert:", "\uD83C\uDFDC\uFE0F"));
        list.add(new EmojiDef(":island:", "\uD83C\uDFDD\uFE0F"));
        list.add(new EmojiDef(":park:", "\uD83C\uDFDE\uFE0F"));
        list.add(new EmojiDef(":stadium:", "\uD83C\uDFDF\uFE0F"));
        list.add(new EmojiDef(":statue:", "\uD83D\uDDFD"));
        list.add(new EmojiDef(":tower:", "\uD83D\uDDFC"));
        list.add(new EmojiDef(":bulb:", "\uD83D\uDCA1"));
        list.add(new EmojiDef(":flashlight:", "\uD83D\uDD26"));
        list.add(new EmojiDef(":book:", "\uD83D\uDCD6"));
        list.add(new EmojiDef(":newspaper:", "\uD83D\uDCF0"));
        list.add(new EmojiDef(":computer:", "\uD83D\uDCBB"));
        list.add(new EmojiDef(":computer_mouse:", "\uD83D\uDDB1\uFE0F"));
        list.add(new EmojiDef(":keyboard:", "\u2328\uFE0F"));
        list.add(new EmojiDef(":phone:", "\uD83D\uDCF1"));
        list.add(new EmojiDef(":email:", "\u2709\uFE0F"));
        list.add(new EmojiDef(":inbox:", "\uD83D\uDCE5"));
        list.add(new EmojiDef(":outbox:", "\uD83D\uDCE4"));
        list.add(new EmojiDef(":package:", "\uD83D\uDCE6"));
        list.add(new EmojiDef(":memo:", "\uD83D\uDCDD"));
        list.add(new EmojiDef(":clipboard:", "\uD83D\uDCCB"));
        list.add(new EmojiDef(":calendar:", "\uD83D\uDCC5"));
        list.add(new EmojiDef(":clock:", "\uD83D\uDD53"));
        list.add(new EmojiDef(":alarm:", "\u23F0"));
        list.add(new EmojiDef(":watch:", "\u231A"));
        list.add(new EmojiDef(":gear:", "\u2699\uFE0F"));
        list.add(new EmojiDef(":wrench:", "\uD83D\uDD27"));
        list.add(new EmojiDef(":hammer:", "\uD83D\uDD28"));
        list.add(new EmojiDef(":tools:", "\uD83D\uDEE0\uFE0F"));
        list.add(new EmojiDef(":laptop:", "\uD83D\uDCBB"));
        list.add(new EmojiDef(":camera:", "\uD83D\uDCF7"));
        list.add(new EmojiDef(":video:", "\uD83D\uDCF9"));
        list.add(new EmojiDef(":tv:", "\uD83D\uDCFA"));
        list.add(new EmojiDef(":radio:", "\uD83D\uDCFB"));
        list.add(new EmojiDef(":speaker:", "\uD83D\uDD0A"));
        list.add(new EmojiDef(":bell:", "\uD83D\uDD14"));
        list.add(new EmojiDef(":no_bell:", "\uD83D\uDD15"));
        list.add(new EmojiDef(":megaphone:", "\uD83D\uDCE3"));
        list.add(new EmojiDef(":loudspeaker:", "\uD83D\uDCE2"));
        list.add(new EmojiDef(":key:", "\uD83D\uDD11"));
        list.add(new EmojiDef(":lock:", "\uD83D\uDD12"));
        list.add(new EmojiDef(":unlock:", "\uD83D\uDD13"));
        list.add(new EmojiDef(":magnifying_glass:", "\uD83D\uDD0E"));
        list.add(new EmojiDef(":link:", "\uD83D\uDD17"));
        list.add(new EmojiDef(":scissors:", "\u2702\uFE0F"));
        list.add(new EmojiDef(":bomb:", "\uD83D\uDCA3"));
        list.add(new EmojiDef(":syringe:", "\uD83D\uDC89"));
        list.add(new EmojiDef(":pill:", "\uD83D\uDC8A"));
        list.add(new EmojiDef(":moneybag:", "\uD83D\uDCB0"));
        list.add(new EmojiDef(":dollar:", "\uD83D\uDCB5"));
        list.add(new EmojiDef(":credit_card:", "\uD83D\uDCB3"));
        list.add(new EmojiDef(":chart:", "\uD83D\uDCCA"));
        list.add(new EmojiDef(":gem:", "\uD83D\uDC8E"));
        list.add(new EmojiDef(":gift:", "\uD83C\uDF81"));
        list.add(new EmojiDef(":balloon:", "\uD83C\uDF88"));
        list.add(new EmojiDef(":tada:", "\uD83C\uDF89"));
        list.add(new EmojiDef(":confetti:", "\uD83C\uDF8A"));
        list.add(new EmojiDef(":crown:", "\uD83D\uDC51"));
        list.add(new EmojiDef(":flag_white:", "\uD83C\uDFF3\uFE0F"));
        list.add(new EmojiDef(":flag_black:", "\uD83C\uDFF4"));
        list.add(new EmojiDef(":rainbow_flag:", "\uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08"));
        list.add(new EmojiDef(":checkered_flag:", "\uD83C\uDFC1"));
        list.add(new EmojiDef(":triangular_flag:", "\uD83D\uDEA9"));
        list.add(new EmojiDef(":crossed_flags:", "\uD83C\uDF8C"));
        list.add(new EmojiDef(":pirate_flag:", "\uD83C\uDFF4\u200D\u2620\uFE0F"));
        return list;
    }
}
