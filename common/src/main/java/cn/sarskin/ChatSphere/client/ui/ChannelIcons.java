package cn.sarskin.ChatSphere.client.ui;

import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.image.ChatImageCache;
import cn.sarskin.ChatSphere.client.image.ChatImageClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Channel icon drawing shared by the rail, sidebar, info header and settings preview.
 *
 * <p>Every caller asks for the same square at a different size, so the geometry lives here once
 * rather than four times. When a channel has no uploaded icon the initial is drawn on a plate,
 * because a bare letter on the row background read as stray text rather than as a missing icon.</p>
 */
public final class ChannelIcons {
    /**
     * Plate behind the fallback initial. Deliberately one fixed neutral: the earlier version
     * picked a colour per channel id, which turned the rail into a wall of tinted squares that
     * fought the unread dot and the active highlight. A single mid grey stays put across themes
     * and still separates from every row background, dark or light.
     */
    private static final int FALLBACK_PLATE = 0xFF6E6E7A;

    /** Glyph on the plate: near-white reads on the grey above in both themes. */
    private static final int FALLBACK_GLYPH = 0xFFF2F2F5;

    /**
     * Corner radius as a share of the icon side. Uploaded icons bake their corners into the
     * texture at {@code ChatImageCache.ICON_CORNER}, so the fallback plate has to use the same
     * share of the side; a fixed pixel radius made the two corners differ on every size.
     */
    private static final float CORNER_RATIO = ChatImageCache.ICON_CORNER;

    /** Radius for one icon side, matching the baked-corner proportion of uploaded icons. */
    public static int cornerRadius(int size) {
        return Math.max(0, Math.min(size / 2, (int) Math.round(size * CORNER_RATIO)));
    }

    private ChannelIcons() {}

    /** Uploaded icon id, or empty when the channel only has a fallback. */
    public static String iconIdOf(String channelId) {
        if (channelId == null) return "";
        ChatDataStore.ChannelConfig cfg = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        String id = cfg != null ? cfg.iconId : "";
        return id != null ? id : "";
    }

    /**
     * The single character drawn when no icon exists. Mirrors the rule the rail has always used:
     * first character of the display name, upper cased, falling back to the raw id without '#'.
     */
    public static String initialOf(String channelId) {
        if (channelId == null) return "#";
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        Component name = history.getConversationDisplayName(channelId);
        String str = name != null ? name.getString() : "";
        if (str.isEmpty()) str = channelId.startsWith("#") ? channelId.substring(1) : channelId;
        if (str.isEmpty()) return "#";
        return str.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    /**
     * Draws the icon, or the initial when the channel has none. The rail's own pill stays behind
     * the initial, so no plate is drawn here.
     */
    public static void draw(GuiGraphics g, String channelId, int x, int y, int size, int color) {
        if (channelId == null || size <= 0) return;
        if (drawUploaded(g, channelId, x, y, size)) return;
        drawGlyph(g, initialOf(channelId), x, y, size, color);
    }

    /**
     * Draws the initial on the fixed plate. For spots with nothing behind the icon, such as the
     * settings preview, where a bare letter would look like stray text rather than a placeholder.
     *
     * <p>The corner radius follows the icon side rather than the caller's hint, so the plate and
     * the uploaded picture beside it round by the same amount.</p>
     */
    public static void drawPlated(GuiGraphics g, String channelId, int x, int y, int size, int radius) {
        if (channelId == null || size <= 0) return;
        if (drawUploaded(g, channelId, x, y, size)) return;
        int r = cornerRadius(size);
        if (r > 0) {
            Ui.fillRoundedRect(g, x, y, size, size, r, FALLBACK_PLATE);
        } else {
            g.fill(x, y, x + size, y + size, FALLBACK_PLATE);
        }
        drawGlyph(g, initialOf(channelId), x, y, size, FALLBACK_GLYPH);
    }

    /**
     * Uploaded icon: the picture already carries a rounded, anti-aliased alpha from the cache, so
     * this is a plain blit. The corners show whatever the caller drew underneath, which is why no
     * corner colour is needed here.
     * Returns false while the bytes are in flight, so the caller draws the initial instead.
     */
    private static boolean drawUploaded(GuiGraphics g, String channelId, int x, int y, int size) {
        String iconId = iconIdOf(channelId);
        if (iconId.isEmpty()) return false;
        ResourceLocation tex = ChatImageCache.iconTexture(iconId);
        if (tex == null) {
            if (!ChatImageCache.cached(iconId) && !ChatImageCache.requested(iconId)
                    && !ChatImageCache.missing(iconId)) {
                ChatImageClient.request(iconId);
            }
            return false;
        }
        int side = ChatImageCache.ICON_TEX;
        g.blit(tex, x, y, size, size, 0f, 0f, side, side, side, side);
        return true;
    }

    private static void drawGlyph(GuiGraphics g, String initial, int x, int y, int size, int color) {
        var font = Minecraft.getInstance().font;
        int textW = font.width(initial);
        // Glyph centring uses the 8px glyph height, not the line height
        g.drawString(font, initial, x + (size - textW) / 2, y + (size - 8) / 2, color, false);
    }
}
