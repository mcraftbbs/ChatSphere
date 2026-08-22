package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.emoji.CustomEmoji;
import cn.sarskin.ChatSphere.client.emoji.CustomEmojiRegistry;
import cn.sarskin.ChatSphere.client.emoji.EmojiEntry;
import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.client.screen.CustomEmojiScreen;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Emoji picker: built-in (Unicode) vs custom (local + server-shared) groups.
 */
public class EmojiPanel {
    private static final int COLS = 8;
    private static final int CELL = 20;
    private static final int ROWS_VISIBLE = 5;
    private static final int GROUP_H = 18;
    private static final int CATEGORY_HEIGHT = 18;
    private static final int SEARCH_HEIGHT = 14;
    private static final int HINT_HEIGHT = 14;
    private static final int CUSTOM_COLS = 4;
    private static final int CUSTOM_ROW_H = 48;
    private static final int CUSTOM_VISIBLE_ROWS = 2;

    /** Panel width fits both the built-in 8x20 grid and the custom 4x48 grid. */
    private static final int PANEL_W = 4 * 48 + 12;
    private static final int NO_CATEGORY = -1;
    private static final int GROUP_BUILTIN = 0;
    private static final int GROUP_CUSTOM = 1;

    public boolean visible;
    private int scrollOffset;
    private int categoryScroll;
    private String filter = "";
    private String filterText = "";
    private EmojiEntry hoveredEmoji;
    private int selectedCategory = NO_CATEGORY;
    private int selectedGroup = GROUP_BUILTIN;
    private boolean editingFilter;

    private static final int CATEGORY_AREA_LEFT = 4;
    private static final int CATEGORY_AREA_RIGHT_OFFSET = 4;

    public EmojiPanel() {
    }

    /** Grid height; the custom group's 48px rows need a taller grid. */
    private int gridH() {
        return selectedGroup == GROUP_CUSTOM ? CELL + CUSTOM_VISIBLE_ROWS * CUSTOM_ROW_H : ROWS_VISIBLE * CELL;
    }

    /** Full panel height; taller while the custom group is open. */
    public int panelH() {
        return GROUP_H + CATEGORY_HEIGHT + SEARCH_HEIGHT + gridH() + HINT_HEIGHT;
    }

    /** Line count the grid can show; custom group = 1 header + N emoji rows. */
    private int visibleLines() {
        return selectedGroup == GROUP_CUSTOM ? 1 + CUSTOM_VISIBLE_ROWS : ROWS_VISIBLE;
    }

    /** Real vertical size of one line; custom emoji rows are 48px tall. */
    private static int lineHeight(LineEntry line) {
        return line.isCategory() ? CELL : (isCustomRow(line) ? CUSTOM_ROW_H : CELL);
    }

    private static boolean isCustomRow(LineEntry line) {
        return line.emojis.length > 0 && line.emojis[0] != null
                && CustomEmojiRegistry.isCustom(line.emojis[0].shortcode());
    }

    /** Y offset of line i from the first drawn line, summing real line heights. */
    private static int lineYAt(List<LineEntry> lines, int start, int i) {
        int y = 0;
        for (int j = start; j < i; j++) {
            y += lineHeight(lines.get(j));
        }
        return y;
    }

    public void render(GuiGraphics g, int panelX, int panelY, int mouseX, int mouseY) {
        if (!visible) return;

        hoveredEmoji = null;

        int radius = Theme.cardRadius();
        Ui.fillRoundedRect(g, panelX, panelY, PANEL_W, panelH(), radius, Theme.popupBg());
        if (Theme.popupBorderVisible()) {
            Ui.renderRoundedOutline(g, panelX, panelY, PANEL_W, panelH(), radius, Theme.popupOutline());
        }

        var font = Minecraft.getInstance().font;

        drawGroupTabs(g, panelX, panelY, mouseX, mouseY);

        int secondRowY = panelY + GROUP_H;
        if (selectedGroup == GROUP_BUILTIN) {
            drawCategoryTabs(g, panelX, secondRowY, mouseX, mouseY);
        } else {
            g.drawString(font, Component.translatable("emoji.panel.group_custom"),
                    panelX + 6, secondRowY + 4, Theme.textInactive(), false);
        }

        int searchY = panelY + GROUP_H + CATEGORY_HEIGHT;
        g.fill(panelX + 3, searchY + 1, panelX + PANEL_W - 3, searchY + SEARCH_HEIGHT - 1, Theme.inputBg());
        String searchDisplay = editingFilter ? filterText + (System.currentTimeMillis() / 600 % 2 == 0 ? "|" : "") : (filterText.isEmpty()
                ? Component.translatable("emoji.panel.search_hint").getString() : filterText);
        int searchColor = filterText.isEmpty() ? Theme.searchPlaceholder() : Theme.text();
        g.drawString(font, searchDisplay, panelX + 6, searchY + 3, searchColor, false);
        boolean manageHover = CustomEmojiRegistry.enabled()
                && mouseX >= panelX + PANEL_W - 17 && mouseX < panelX + PANEL_W - 3
                && mouseY >= searchY + 1 && mouseY < searchY + SEARCH_HEIGHT - 1;
        if (manageHover) {
            Ui.fillRoundedRect(g, panelX + PANEL_W - 17, searchY + 1, 14, SEARCH_HEIGHT - 2, 3, Theme.hoverRow());
        }
        if (CustomEmojiRegistry.enabled()) {
            g.drawString(font, "+", panelX + PANEL_W - 13, searchY + 2,
                manageHover ? Theme.text() : Theme.textInactive(), false);
        }

        int gridY = searchY + SEARCH_HEIGHT;
        int gridHeight = gridH();
        g.enableScissor(panelX + 2, gridY, panelX + PANEL_W - 2, gridY + gridHeight);

        List<LineEntry> lines = buildLines();

        int lineY = gridY + 2;
        int startLine = scrollOffset;
        int endLine = Math.min(lines.size(), startLine + visibleLines() + 2);

        for (int i = startLine; i < endLine; i++) {
            int ly = lineY + lineYAt(lines, startLine, i);
            LineEntry line = lines.get(i);
            if (line.isCategory()) {
                g.drawString(font, line.categoryName, panelX + 6, ly + 4, Theme.textInactive(), false);
            } else {
                EmojiEntry[] row = line.emojis;
                boolean customRow = isCustomRow(line);
                int cols = customRow ? CUSTOM_COLS : COLS;
                int cell = customRow ? CUSTOM_ROW_H : CELL;
                for (int col = 0; col < cols; col++) {
                    EmojiEntry e = col < row.length ? row[col] : null;
                    if (e == null) continue;
                    int ex = panelX + 6 + col * cell;
                    int ey = ly;
                    boolean hover = mouseX >= ex && mouseX < ex + cell && mouseY >= ey && mouseY < ey + cell;
                    if (hover) {
                        g.fill(ex, ey, ex + cell, ey + cell, Theme.emojiCellBg());
                        hoveredEmoji = e;
                    }
                    if (CustomEmojiRegistry.isCustom(e.shortcode())) {
                        CustomEmoji ce = CustomEmojiRegistry.byShortcode(
                                e.shortcode().substring(1, e.shortcode().length() - 1));
                        if (ce != null) {
                            // Keep the emoji's own aspect ratio inside the cell.
                            int max = cell - 4;
                            int ew = ce.width(), eh = ce.height();
                            double s = Math.min(1.0, Math.min((double) max / ew, (double) max / eh));
                            int pw = Math.max(12, (int) Math.round(ew * s));
                            int ph = Math.max(12, (int) Math.round(eh * s));
                            ce.blit(g, ex + 2, ey + (cell - ph) / 2, pw, ph);
                            continue;
                        }
                    }
                    String pua = EmojiRegistry.puaChar(e);
                    if (pua != null) {
                        g.drawString(font, Component.literal(pua).withStyle(EmojiRegistry.EMOJI_STYLE), ex + 2, ey + 2 + EmojiRegistry.EMOJI_Y_OFFSET, 0xFFFFFF, false);
                    } else {
                        g.drawString(font, e.unicode(), ex + 2, ey + 2, 0xFFFFFF, false);
                    }
                }
            }
        }

        g.disableScissor();

        int hintY = gridY + gridHeight;
        g.fill(panelX + 2, hintY, panelX + PANEL_W - 2, hintY + HINT_HEIGHT, Theme.inputBg());
        g.enableScissor(panelX + 2, hintY, panelX + PANEL_W - 2, hintY + HINT_HEIGHT);
        if (hoveredEmoji != null) {
            Component hint;
            if (CustomEmojiRegistry.isCustom(hoveredEmoji.shortcode())) {
                hint = Component.literal(hoveredEmoji.shortcode())
                        .append(Component.translatable("emoji.panel.custom_hint").withStyle(ChatFormatting.GRAY));
            } else {
                hint = Component.literal(hoveredEmoji.unicode() + " " + hoveredEmoji.shortcode() + " ")
                        .append(Component.literal(hoveredEmoji.name()).withStyle(ChatFormatting.GRAY));
            }
            g.drawString(font, font.substrByWidth(hint, PANEL_W - 10).getString(), panelX + 5, hintY + 3, 0xFFFFCC00, false);
        } else {
            int total = EmojiRegistry.getAll().size() + CustomEmojiRegistry.list().size();
            g.drawString(font, Component.translatable("emoji.panel.emoji_count", total), panelX + 5, hintY + 3, Theme.searchPlaceholder(), false);
        }
        g.disableScissor();

        int totalLineHeight = lines.size();
        int visLineCount = visibleLines();
        if (totalLineHeight > visLineCount) {
            int barH = Math.max(8, (visLineCount * gridHeight) / totalLineHeight);
            int barY = gridY + (scrollOffset * (gridHeight - barH)) / (totalLineHeight - visLineCount);
            g.fill(panelX + PANEL_W - 3, barY, panelX + PANEL_W - 1, barY + barH, Theme.scrollThumb());
        }
    }

    private void drawGroupTabs(GuiGraphics g, int panelX, int panelY, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        int tabY = panelY + 2;
        int tabH = GROUP_H - 4;
        int areaX0 = panelX + CATEGORY_AREA_LEFT;
        String[] keys = {"emoji.panel.group_builtin", "emoji.panel.group_custom"};
        int tabs = CustomEmojiRegistry.enabled() ? keys.length : 1;
        int x = areaX0;
        for (int gi = 0; gi < tabs; gi++) {
            Component label = Component.translatable(keys[gi]);
            int tw = font.width(label) + 12;
            boolean selected = selectedGroup == gi;
            boolean hover = mouseX >= x && mouseX < x + tw && mouseY >= tabY && mouseY < tabY + tabH;
            int bg = selected ? Theme.emojiTabSel() : (hover ? Theme.emojiTabHover() : Theme.emojiTabBg());
            Ui.fillRoundedRect(g, x, tabY, tw, tabH, 3, bg);
            g.drawString(font, label, x + 6, tabY + 2, selected ? Theme.text() : Theme.textInactive(), false);
            x += tw + 4;
        }
    }

    private List<LineEntry> buildLines() {
        List<LineEntry> result = new ArrayList<>();

        if (selectedGroup == GROUP_CUSTOM) {
            if (!CustomEmojiRegistry.enabled()) return result;
            List<EmojiEntry> custom = CustomEmojiRegistry.listVisible().stream()
                    .map(c -> new EmojiEntry(c.token(), "", "Custom", c.token()))
                    .filter(e -> filter == null || filter.isEmpty()
                            || e.shortcode().toLowerCase().contains(filter))
                    .toList();
            if (!custom.isEmpty()) {
                result.add(new LineEntry(Component.translatable("emoji.panel.group_custom").getString(), null));
                EmojiEntry[] row = new EmojiEntry[4];
                int idx = 0;
                for (EmojiEntry e : custom) {
                    row[idx++] = e;
                    if (idx >= 4) {
                        result.add(new LineEntry(null, row));
                        row = new EmojiEntry[4];
                        idx = 0;
                    }
                }
                if (idx > 0) {
                    result.add(new LineEntry(null, row));
                }
            }
            return result;
        }

        List<String> cats = EmojiRegistry.getCategories();
        if (selectedCategory >= 0 && selectedCategory < cats.size()) {
            cats = List.of(cats.get(selectedCategory));
        }
        if (filter != null && !filter.isEmpty()) {
            cats = cats.stream()
                    .filter(c -> EmojiRegistry.getVisibleByCategory(c).stream()
                            .anyMatch(e -> e.shortcode().toLowerCase().contains(filter)
                                    || e.name().toLowerCase().contains(filter)))
                    .toList();
        }
        for (String cat : cats) {
            result.add(new LineEntry(Component.translatable(EmojiRegistry.categoryLangKey(cat)).getString(), null));
            List<EmojiEntry> emojis = EmojiRegistry.getVisibleByCategory(cat);
            if (filter != null && !filter.isEmpty()) {
                emojis = emojis.stream()
                        .filter(e -> e.shortcode().toLowerCase().contains(filter)
                                || e.name().toLowerCase().contains(filter))
                        .toList();
            }
            EmojiEntry[] row = new EmojiEntry[COLS];
            int idx = 0;
            for (EmojiEntry e : emojis) {
                row[idx++] = e;
                if (idx >= COLS) {
                    result.add(new LineEntry(null, row));
                    row = new EmojiEntry[COLS];
                    idx = 0;
                }
            }
            if (idx > 0) {
                result.add(new LineEntry(null, row));
            }
        }
        return result;
    }

    private void drawCategoryTabs(GuiGraphics g, int panelX, int rowY, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        List<String> cats = EmojiRegistry.getCategories();
        int tabY = rowY + 2;
        int tabH = CATEGORY_HEIGHT - 4;
        int areaX0 = panelX + CATEGORY_AREA_LEFT;
        int areaX1 = panelX + PANEL_W - CATEGORY_AREA_RIGHT_OFFSET;

        g.enableScissor(areaX0, tabY, areaX1, tabY + tabH);

        int totalW = 0;
        int[] tabWidths = new int[cats.size() + 1];
        tabWidths[0] = font.width(Component.literal("\u00AB")) + 12;
        totalW += tabWidths[0];
        for (int i = 0; i < cats.size(); i++) {
            tabWidths[i + 1] = font.width(Component.translatable(EmojiRegistry.categoryLangKey(cats.get(i)))) + 12;
            totalW += tabWidths[i + 1];
        }
        int availW = areaX1 - areaX0;
        int maxCatScroll = Math.max(0, totalW - availW);
        categoryScroll = Math.max(0, Math.min(categoryScroll, maxCatScroll));

        int x = areaX0 - categoryScroll;
        // "All" tab
        {
            boolean allSelected = selectedCategory == NO_CATEGORY;
            int tw = tabWidths[0];
            boolean hover = mouseX >= x && mouseX < x + tw && mouseY >= tabY && mouseY < tabY + tabH;
            int bg = allSelected ? Theme.emojiTabSel() : (hover ? Theme.emojiTabHover() : Theme.emojiTabBg());
            Ui.fillRoundedRect(g, x, tabY, tw, tabH, 3, bg);
            g.drawString(font, "\u00AB", x + (tw - font.width("\u00AB")) / 2, tabY + 2, allSelected ? Theme.text() : Theme.textInactive(), false);
            x += tw;
        }
        for (int i = 0; i < cats.size(); i++) {
            String cat = cats.get(i);
            var label = Component.translatable(EmojiRegistry.categoryLangKey(cat));
            boolean selected = i == selectedCategory;
            int tw = tabWidths[i + 1];
            boolean hover = mouseX >= x && mouseX < x + tw && mouseY >= tabY && mouseY < tabY + tabH;
            int bg = selected ? Theme.emojiTabSel() : (hover ? Theme.emojiTabHover() : Theme.emojiTabBg());
            Ui.fillRoundedRect(g, x, tabY, tw, tabH, 3, bg);
            g.drawString(font, label, x + 4, tabY + 2, selected ? Theme.text() : Theme.textInactive(), false);
            x += tw;
        }

        g.disableScissor();

        if (categoryScroll > 0) {
            g.drawString(font, "<", areaX0 - 1, tabY + 1, 0x88AAAAAA, false);
        }
        if (categoryScroll < maxCatScroll) {
            g.drawString(font, ">", areaX1 - font.width(">") - 1, tabY + 1, 0x88AAAAAA, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int panelX, int panelY, EditBox input) {
        if (!visible || button != 0) return false;

        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (mx < panelX || mx > panelX + PANEL_W || my < panelY || my > panelY + panelH()) {
            visible = false;
            return false;
        }

        {
            var font = Minecraft.getInstance().font;
            String[] keys = {"emoji.panel.group_builtin", "emoji.panel.group_custom"};
            int tabY = panelY + 2;
            int tabH = GROUP_H - 4;
            int x = panelX + CATEGORY_AREA_LEFT;
            for (int gi = 0; gi < (CustomEmojiRegistry.enabled() ? 2 : 1); gi++) {
                int tw = font.width(Component.translatable(keys[gi])) + 12;
                if (mx >= x && mx < x + tw && my >= tabY && my < tabY + tabH) {
                    selectedGroup = gi;
                    selectedCategory = NO_CATEGORY;
                    filter = "";
                    filterText = "";
                    editingFilter = false;
                    scrollOffset = 0;
                    categoryScroll = 0;
                    return true;
                }
                x += tw + 4;
            }
        }

        if (selectedGroup == GROUP_BUILTIN) {
            var font = Minecraft.getInstance().font;
            List<String> cats = EmojiRegistry.getCategories();
            int tabY = panelY + GROUP_H + 2;
            int tabH = CATEGORY_HEIGHT - 4;
            int areaX0 = panelX + CATEGORY_AREA_LEFT;
            int x = areaX0 - categoryScroll;
            // "All" tab
            {
                int tw = font.width("\u00AB") + 12;
                if (mx >= x && mx < x + tw && my >= tabY && my < tabY + tabH) {
                    selectedCategory = NO_CATEGORY;
                    filter = "";
                    filterText = "";
                    editingFilter = false;
                    scrollOffset = 0;
                    categoryScroll = 0;
                    return true;
                }
                x += tw;
            }
            for (int i = 0; i < cats.size(); i++) {
                String cat = cats.get(i);
                var label = Component.translatable(EmojiRegistry.categoryLangKey(cat));
                int tw = font.width(label) + 12;
                if (mx >= x && mx < x + tw && my >= tabY && my < tabY + tabH) {
                    selectedCategory = (selectedCategory == i) ? NO_CATEGORY : i;
                    filter = "";
                    filterText = "";
                    editingFilter = false;
                    if (selectedCategory == NO_CATEGORY) {
                        scrollOffset = 0;
                        categoryScroll = 0;
                    } else {
                        scrollToCategory(cat);
                    }
                    return true;
                }
                x += tw;
            }
        }

        int searchY = panelY + GROUP_H + CATEGORY_HEIGHT;
        if (my >= searchY && my <= searchY + SEARCH_HEIGHT) {
            // Manage custom emoji button wins over focusing the search box
            if (CustomEmojiRegistry.enabled() && mx >= panelX + PANEL_W - 17 && mx <= panelX + PANEL_W - 3) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen != null) {
                    visible = false;
                    mc.setScreen(new CustomEmojiScreen(mc.screen, CustomEmojiRegistry.currentChannel()));
                }
                return true;
            }
            editingFilter = true;
            return true;
        }

        int gridY = searchY + SEARCH_HEIGHT;
        if (my >= gridY && my < gridY + gridH()) {
            List<LineEntry> lines = buildLines();
            int ly = gridY + 2;
            for (int lineIdx = scrollOffset; lineIdx < lines.size(); lineIdx++) {
                LineEntry line = lines.get(lineIdx);
                int h = lineHeight(line);
                if (my >= ly && my < ly + h) {
                    if (!line.isCategory()) {
                        int cell = isCustomRow(line) ? CUSTOM_ROW_H : CELL;
                        int col = (mx - panelX - 6) / cell;
                        if (col >= 0 && col < line.emojis.length && line.emojis[col] != null) {
                            EmojiEntry entry = line.emojis[col];
                            input.setValue(input.getValue() + entry.shortcode());
                            input.moveCursorToEnd(false);
                            visible = false;
                            return true;
                        }
                    }
                    break;
                }
                ly += h;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, int panelX, int panelY, double scrollY) {
        if (!visible) return false;

        if (selectedGroup == GROUP_BUILTIN) {
            int tabY = panelY + GROUP_H + 2;
            int tabH = CATEGORY_HEIGHT - 4;
            int areaX0 = panelX + CATEGORY_AREA_LEFT;
            int areaX1 = panelX + PANEL_W - CATEGORY_AREA_RIGHT_OFFSET;
            if (mouseX >= areaX0 && mouseX < areaX1 && mouseY >= tabY && mouseY < tabY + tabH) {
                categoryScroll -= (int) scrollY * 20;
                return true;
            }
        }

        int lineCount = buildLines().size();
        int maxScroll = Math.max(0, lineCount - visibleLines());
        scrollOffset = Mth.clamp(scrollOffset - (int) scrollY, 0, maxScroll);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible) return false;
        if (editingFilter) {
            if (keyCode == 256) {
                editingFilter = false;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                editingFilter = false;
                filter = filterText.toLowerCase();
                scrollOffset = 0;
                return true;
            }
            if (keyCode == 259 && !filterText.isEmpty()) {
                filterText = filterText.substring(0, filterText.length() - 1);
                filter = filterText.toLowerCase();
                scrollOffset = 0;
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char codePoint) {
        if (!visible || !editingFilter) return false;
        filterText += codePoint;
        filter = filterText.toLowerCase();
        scrollOffset = 0;
        return true;
    }

    private void scrollToCategory(String category) {
        String display = Component.translatable(EmojiRegistry.categoryLangKey(category)).getString();
        List<LineEntry> lines = buildLines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).isCategory() && lines.get(i).categoryName.equals(display)) {
                scrollOffset = Math.max(0, i);
                break;
            }
        }
    }

    public void toggle() {
        visible = !visible;
        if (visible) {
            scrollOffset = 0;
            categoryScroll = 0;
            selectedCategory = NO_CATEGORY;
            selectedGroup = GROUP_BUILTIN;
            filterText = "";
            filter = "";
            editingFilter = false;
        }
    }

    private record LineEntry(String categoryName, EmojiEntry[] emojis) {
        boolean isCategory() { return categoryName != null; }
    }
}
