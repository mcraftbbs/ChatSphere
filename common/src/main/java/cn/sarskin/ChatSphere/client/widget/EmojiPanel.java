package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.emoji.EmojiEntry;
import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.client.ui.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class EmojiPanel {
    private static final int COLS = 8;
    private static final int CELL = 20;
    private static final int ROWS_VISIBLE = 5;
    private static final int CATEGORY_HEIGHT = 18;
    private static final int SEARCH_HEIGHT = 14;
    private static final int HINT_HEIGHT = 14;

    private static final int PANEL_W = COLS * CELL + 12;
    public static final int PANEL_H = CATEGORY_HEIGHT + SEARCH_HEIGHT + ROWS_VISIBLE * CELL + HINT_HEIGHT;
    private static final int NO_CATEGORY = -1;

    public boolean visible;
    private int scrollOffset;
    private int categoryScroll;
    private String filter = "";
    private String filterText = "";
    private EmojiEntry hoveredEmoji;
    private int selectedCategory = NO_CATEGORY;
    private boolean editingFilter;

    private static final int CATEGORY_TAB_START = 4;
    private static final int CATEGORY_AREA_LEFT = 4;
    private static final int CATEGORY_AREA_RIGHT_OFFSET = 4;

    public EmojiPanel() {
    }

    public void render(GuiGraphics g, int panelX, int panelY, int mouseX, int mouseY) {
        if (!visible) return;

        hoveredEmoji = null;

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, Theme.panelBg2());
        g.renderOutline(panelX, panelY, PANEL_W, PANEL_H, Theme.emojiOutline());

        var font = Minecraft.getInstance().font;

        drawCategoryTabs(g, panelX, panelY, mouseX, mouseY);

        int searchY = panelY + CATEGORY_HEIGHT;
        g.fill(panelX + 3, searchY + 1, panelX + PANEL_W - 3, searchY + SEARCH_HEIGHT - 1, Theme.inputBg());
        String searchDisplay = editingFilter ? filterText + (System.currentTimeMillis() / 600 % 2 == 0 ? "|" : "") : (filterText.isEmpty()
                ? Component.translatable("emoji.panel.search_hint").getString() : filterText);
        int searchColor = filterText.isEmpty() ? Theme.searchPlaceholder() : Theme.text();
        g.drawString(font, searchDisplay, panelX + 6, searchY + 3, searchColor, false);

        int gridY = searchY + SEARCH_HEIGHT;
        int gridH = ROWS_VISIBLE * CELL;
        g.enableScissor(panelX + 2, gridY, panelX + PANEL_W - 2, gridY + gridH);

        List<LineEntry> lines = buildLines();

        int lineY = gridY + 2;
        int startLine = scrollOffset;
        int endLine = Math.min(lines.size(), startLine + ROWS_VISIBLE + 2);

        for (int i = startLine; i < endLine; i++) {
            int ly = lineY + (i - startLine) * CELL;
            LineEntry line = lines.get(i);
            if (line.isCategory()) {
                g.drawString(font, line.categoryName, panelX + 6, ly + 4, Theme.textInactive(), false);
            } else {
                EmojiEntry[] row = line.emojis;
                for (int col = 0; col < row.length; col++) {
                    EmojiEntry e = row[col];
                    if (e == null) continue;
                    int ex = panelX + 6 + col * CELL;
                    int ey = ly;
                    boolean hover = mouseX >= ex && mouseX < ex + CELL && mouseY >= ey && mouseY < ey + CELL;
                    if (hover) {
                        g.fill(ex, ey, ex + CELL, ey + CELL, Theme.emojiCellBg());
                        hoveredEmoji = e;
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

        int hintY = gridY + gridH;
        g.fill(panelX + 2, hintY, panelX + PANEL_W - 2, hintY + HINT_HEIGHT, Theme.inputBg());
        g.enableScissor(panelX + 2, hintY, panelX + PANEL_W - 2, hintY + HINT_HEIGHT);
        if (hoveredEmoji != null) {
            Component hint = Component.literal(hoveredEmoji.unicode() + " " + hoveredEmoji.shortcode() + " ")
                    .append(Component.literal(hoveredEmoji.name()).withStyle(ChatFormatting.GRAY));
            g.drawString(font, font.substrByWidth(hint, PANEL_W - 10).getString(), panelX + 5, hintY + 3, 0xFFFFCC00, false);
        } else {
            g.drawString(font, Component.translatable("emoji.panel.emoji_count", EmojiRegistry.getAll().size()), panelX + 5, hintY + 3, Theme.searchPlaceholder(), false);
        }
        g.disableScissor();

        int totalLineHeight = lines.size();
        int visLineCount = ROWS_VISIBLE;
        if (totalLineHeight > visLineCount) {
            int barH = Math.max(8, (visLineCount * gridH) / totalLineHeight);
            int barY = gridY + (scrollOffset * (gridH - barH)) / (totalLineHeight - visLineCount);
            g.fill(panelX + PANEL_W - 3, barY, panelX + PANEL_W - 1, barY + barH, Theme.scrollThumb());
        }
    }

    private List<LineEntry> buildLines() {
        List<LineEntry> result = new ArrayList<>();
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
            result.add(new LineEntry(cat, null));
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

    private void drawCategoryTabs(GuiGraphics g, int panelX, int panelY, int mouseX, int mouseY) {
        var font = Minecraft.getInstance().font;
        List<String> cats = EmojiRegistry.getCategories();
        int tabY = panelY + 2;
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
            g.fill(x, tabY, x + tw, tabY + tabH, bg);
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
            g.fill(x, tabY, x + tw, tabY + tabH, bg);
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

        if (mx < panelX || mx > panelX + PANEL_W || my < panelY || my > panelY + PANEL_H) {
            visible = false;
            return false;
        }

        // Category tabs
        {
            var font = Minecraft.getInstance().font;
            List<String> cats = EmojiRegistry.getCategories();
            int tabY = panelY + 2;
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

        // Search bar
        int searchY = panelY + CATEGORY_HEIGHT;
        if (my >= searchY && my <= searchY + SEARCH_HEIGHT) {
            editingFilter = true;
            return true;
        }

        // Emoji grid
        int gridY = searchY + SEARCH_HEIGHT;
        if (my >= gridY && my < gridY + ROWS_VISIBLE * CELL) {
            List<LineEntry> lines = buildLines();
            int relRow = (my - gridY - 2) / CELL;
            int lineIdx = scrollOffset + relRow;
            if (lineIdx >= 0 && lineIdx < lines.size()) {
                LineEntry line = lines.get(lineIdx);
                if (!line.isCategory()) {
                    int col = (mx - panelX - 6) / CELL;
                    if (col >= 0 && col < line.emojis.length && line.emojis[col] != null) {
                        EmojiEntry entry = line.emojis[col];
                        input.setValue(input.getValue() + entry.shortcode());
                        input.moveCursorToEnd(false);
                        visible = false;
                        return true;
                    }
                }
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, int panelX, int panelY, double scrollY) {
        if (!visible) return false;

        int tabY = panelY + 2;
        int tabH = CATEGORY_HEIGHT - 4;
        int areaX0 = panelX + CATEGORY_AREA_LEFT;
        int areaX1 = panelX + PANEL_W - CATEGORY_AREA_RIGHT_OFFSET;
        if (mouseX >= areaX0 && mouseX < areaX1 && mouseY >= tabY && mouseY < tabY + tabH) {
            categoryScroll -= (int) scrollY * 20;
            return true;
        }

        int lineCount = buildLines().size();
        int maxScroll = Math.max(0, lineCount - ROWS_VISIBLE);
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
        List<LineEntry> lines = buildLines();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).isCategory() && lines.get(i).categoryName.equals(category)) {
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
            filterText = "";
            filter = "";
            editingFilter = false;
        }
    }

    private record LineEntry(String categoryName, EmojiEntry[] emojis) {
        boolean isCategory() { return categoryName != null; }
    }
}
