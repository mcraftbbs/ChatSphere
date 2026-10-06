package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.client.ConsoleTabs;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.ui.UiToggle;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.mixin.ScreenAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

import static cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG_SPEC;

/** Console tab editor; first row wins, same rules as the json field. */
public class ConsoleTabsScreen extends Screen {
    private static final int PAD = 12;
    private static final int HEADER_H = 36;
    private static final int ROW_H = 24;
    private static final int MOVE_W = 14;
    private static final int FLAG_W = 44;
    private static final int DEL_W = 14;

    private final Screen parent;
    private final List<ConsoleTabs.Tab> tabs = new ArrayList<>();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private final List<String> patterns = new ArrayList<>();
    private int scrollOffset;
    private int scrollMax;
    private int editingRow = -1;
    private boolean editingPattern;
    private EditBox cellBox;
    private EditBox newName;
    private EditBox newPattern;
    private int[] matches = new int[0];
    private int totalMessages;
    private String countKey = "";

    public ConsoleTabsScreen(Screen parent) {
        super(Component.translatable("screen.chatsphere.console_tabs.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        tabs.clear();
        tabs.addAll(ConsoleTabs.parse(ModClientConfig.CONFIG.consoleTabs.get()));
        patterns.clear();
        for (ConsoleTabs.Tab tab : tabs) patterns.add(tab.pattern());

        cellBox = new EditBox(font, 0, 0, 100, 16, Component.empty());
        cellBox.setBordered(true);
        cellBox.setMaxLength(160);
        cellBox.setVisible(false);
        addRenderableWidget(cellBox);

        int rowY = height - 54;
        newName = new EditBox(font, PAD, rowY, 110, 18,
                Component.translatable("screen.chatsphere.console_tabs.name"));
        newName.setBordered(true);
        newName.setMaxLength(24);
        addRenderableWidget(newName);

        newPattern = new EditBox(font, PAD + 116, rowY, width - PAD * 2 - 116 - 54, 18,
                Component.translatable("screen.chatsphere.console_tabs.pattern"));
        newPattern.setBordered(true);
        newPattern.setMaxLength(160);
        addRenderableWidget(newPattern);

        addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.console_tabs.add"),
                btn -> addTab())
                .bounds(width - PAD - 50, rowY, 50, 18).build());

        addRenderableWidget(StyledButton.styledBuilder(
                CommonComponents.GUI_BACK,
                btn -> onClose())
                .bounds(width - PAD - 0 - 80, height - 26, 80, 18).build());

        rebuildRows();
    }

    private void rebuildRows() {
        rowWidgets.forEach(this::removeWidget);
        rowWidgets.clear();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            StyledButton up = StyledButton.styledBuilder(Component.literal("\u2191"), b -> move(index, -1))
                    .bounds(0, 0, MOVE_W, 18).tooltip(Component.translatable("screen.chatsphere.console_tabs.move_up")).build();
            StyledButton down = StyledButton.styledBuilder(Component.literal("\u2193"), b -> move(index, 1))
                    .bounds(0, 0, MOVE_W, 18).tooltip(Component.translatable("screen.chatsphere.console_tabs.move_down")).build();
            UiToggle hide = new UiToggle(0, 0, FLAG_W, 16, tabs.get(i).hideFromAll(), value ->
                    setTab(index, new ConsoleTabs.Tab(tabs.get(index).name(), patterns.get(index), value)));
            hide.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("screen.chatsphere.console_tabs.hide_tip")));
            StyledButton del = StyledButton.styledBuilder(Component.literal("\u00d7"), b -> removeTab(index))
                    .bounds(0, 0, DEL_W, 18).style(StyledButton.Style.DANGER)
                    .tooltip(Component.translatable("screen.chatsphere.console_tabs.delete_tip")).build();
            rowWidgets.add(addRenderableWidget(up));
            rowWidgets.add(addRenderableWidget(down));
            rowWidgets.add(addRenderableWidget(hide));
            rowWidgets.add(addRenderableWidget(del));
        }
    }

    private void setTab(int index, ConsoleTabs.Tab tab) {
        tabs.set(index, tab);
        persist();
    }

    private void addTab() {
        String pattern = newPattern.getValue().trim();
        String name = newName.getValue().trim();
        if (pattern.isEmpty()) return;
        if (name.isEmpty()) name = pattern;
        tabs.add(new ConsoleTabs.Tab(name, pattern, false));
        patterns.add(pattern);
        newName.setValue("");
        newPattern.setValue("");
        scrollOffset = Integer.MAX_VALUE;
        persist();
        rebuildRows();
    }

    private void removeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        tabs.remove(index);
        patterns.remove(index);
        if (editingRow == index) stopEditing();
        persist();
        rebuildRows();
    }

    private void move(int index, int dir) {
        int target = index + dir;
        if (target < 0 || target >= tabs.size()) return;
        ConsoleTabs.Tab tab = tabs.remove(index);
        String pattern = patterns.remove(index);
        tabs.add(target, tab);
        patterns.add(target, pattern);
        persist();
        rebuildRows();
    }

    private void persist() {
        ModClientConfig.CONFIG.consoleTabs.set(ConsoleTabs.toJson(tabs));
        CONFIG_SPEC.save();
        ModChatScreen.invalidateConsoleTabs();
        countKey = "";
    }

    private void startEditing(int index, boolean pattern, int x, int y, int w) {
        if (index < 0 || index >= tabs.size()) return;
        editingRow = index;
        editingPattern = pattern;
        cellBox.setX(x);
        cellBox.setY(y + 2);
        cellBox.setWidth(w);
        cellBox.setValue(pattern ? patterns.get(index) : tabs.get(index).name());
        cellBox.setVisible(true);
        setFocused(cellBox);
    }

    private void commitEdit() {
        if (editingRow < 0 || editingRow >= tabs.size()) {
            stopEditing();
            return;
        }
        int index = editingRow;
        String value = cellBox.getValue().trim();
        if (editingPattern) {
            if (!value.isEmpty()) patterns.set(index, value);
        } else if (!value.isEmpty()) {
            tabs.set(index, new ConsoleTabs.Tab(value, patterns.get(index), tabs.get(index).hideFromAll()));
        }
        stopEditing();
        persist();
    }

    private void stopEditing() {
        editingRow = -1;
        if (cellBox != null) {
            cellBox.setVisible(false);
            if (getFocused() == cellBox) setFocused(null);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingRow >= 0 && cellBox != null && cellBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                commitEdit();
                return true;
            }
            if (keyCode == 256) {
                stopEditing();
                return true;
            }
        }
        if ((keyCode == 257 || keyCode == 335) && newPattern != null && newPattern.isFocused()) {
            addTab();
            setFocused(newName);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        scrollMax = Math.max(0, tabs.size() * ROW_H - (height - HEADER_H - 80));
        scrollOffset = Mth.clamp(scrollOffset, 0, scrollMax);
        refreshCounts();

        int iconX = PAD;
        int iconY = (HEADER_H - 18) / 2;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.drawString(font, ">_", iconX + 2, iconY + 6, Theme.accent(), false);

        g.drawString(font, title, iconX + 26, (HEADER_H - 8) / 2, Theme.text(), false);

        Component help = Component.translatable("screen.chatsphere.console_tabs.help");
        g.drawString(font, help, Math.max(width / 2, width - PAD - font.width(help)), (HEADER_H - 8) / 2,
                Theme.textFaint(), false);

        int closeX = width - PAD - 16;
        int closeY = (HEADER_H - 16) / 2;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        Ui.drawCloseIcon(g, closeX, closeY, 16);

        g.fill(PAD, HEADER_H + 2, width - PAD, HEADER_H + 3, Theme.divider());

        if (tabs.isEmpty()) {
            Component empty = Component.translatable("screen.chatsphere.console_tabs.empty");
            g.drawString(font, empty, width / 2 - font.width(empty) / 2, HEADER_H + 24, Theme.textDim(), false);
        } else {
            layoutRows();
            int y = HEADER_H + 6 - scrollOffset;
            for (int i = 0; i < tabs.size(); i++) {
                if (y + ROW_H > HEADER_H && y < height - 60) drawRow(g, i, y, mouseX, mouseY);
                y += ROW_H;
            }
        }

        if (scrollMax > 0) {
            int trackTop = HEADER_H + 8;
            int trackBot = height - 60;
            int trackH = trackBot - trackTop;
            int thumbH = Math.max(12, trackH * trackH / (trackH + scrollMax));
            int thumbY = trackTop + (trackH - thumbH) * scrollOffset / scrollMax;
            g.fill(width - 5, trackTop, width - 2, trackBot, Theme.scrollTrack());
            g.fill(width - 5, thumbY, width - 2, thumbY + thumbH, Theme.scrollThumb());
        }

        for (var renderable : ((ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void drawRow(GuiGraphics g, int index, int y, int mouseX, int mouseY) {
        ConsoleTabs.Tab tab = tabs.get(index);
        String pattern = patterns.get(index);
        int rowW = width - PAD * 2 - 4;
        boolean hovered = mouseY >= y && mouseY < y + ROW_H;
        if (hovered) {
            Ui.fillRoundedRect(g, PAD, y, rowW, ROW_H, 6, Theme.hoverRow());
        }

        boolean bad = !pattern.isEmpty() && !valid(pattern);
        String count = countFor(index);
        // The count owns the band right before the toggle, cells stop short of it
        int countX = countRight() - font.width(count);

        drawCell(g, nameCellX(), y, nameCellW(), tab.name(), index, false, Theme.textMain());
        drawCell(g, patternCellX(), y, patternCellW(countX), pattern, index, true,
                bad ? 0xFFFF6666 : Theme.textDim());
        g.drawString(font, count, countX, y + 8, bad ? 0xFFFF6666 : Theme.textFaint(), false);
    }

    private int nameCellX() {
        return PAD + MOVE_W * 2 + 6;
    }

    private int nameCellW() {
        return 110;
    }

    private int patternCellX() {
        return nameCellX() + nameCellW() + 6;
    }

    private int delX() {
        return width - PAD - DEL_W - 2;
    }

    private int toggleX() {
        return delX() - FLAG_W - 6;
    }

    private int countRight() {
        return toggleX() - 8;
    }

    private int patternCellW(int countX) {
        return Math.max(60, countX - 6 - patternCellX());
    }

    private void drawCell(GuiGraphics g, int x, int y, int w, String text, int index, boolean pattern, int color) {
        boolean editing = editingRow == index && editingPattern == pattern;
        if (editing) return;
        Ui.fillRoundedRect(g, x, y + 2, w, ROW_H - 6, 4, Theme.inputBg());
        String shown = font.plainSubstrByWidth(text, w - 8);
        g.drawString(font, shown, x + 4, y + 8, color, false);
    }

    private static boolean valid(String pattern) {
        try {
            java.util.regex.Pattern.compile(pattern);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void refreshCounts() {
        List<ChatMessageData> messages =
                ChatHistoryManager.getInstance().getMessagesByConversation(ChatHistoryManager.COMMAND_CONVERSATION_ID);
        String key = messages.size() + ":" + patterns.hashCode();
        if (key.equals(countKey)) return;
        countKey = key;
        totalMessages = messages.size();
        matches = new int[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            ConsoleTabs.Tab tab = new ConsoleTabs.Tab(tabs.get(i).name(), patterns.get(i), false);
            int hits = 0;
            for (ChatMessageData message : messages) {
                if (tab.matches(message.plainText())) hits++;
            }
            matches[i] = hits;
        }
    }

    private void layoutRows() {
        int y = HEADER_H + 6 - scrollOffset;
        for (int i = 0; i < tabs.size(); i++) {
            int w = i * 4;
            rowWidgets.get(w).setY(y + 3);
            rowWidgets.get(w).setX(PAD);
            rowWidgets.get(w + 1).setY(y + 3);
            rowWidgets.get(w + 1).setX(PAD + MOVE_W + 2);
            rowWidgets.get(w + 2).setY(y + 4);
            rowWidgets.get(w + 2).setX(toggleX());
            rowWidgets.get(w + 3).setY(y + 3);
            rowWidgets.get(w + 3).setX(delX());
            if (editingRow == i && cellBox != null) {
                if (editingPattern) {
                    cellBox.setX(patternCellX());
                    cellBox.setWidth(patternCellW(countRight() - font.width(countFor(i))));
                } else {
                    cellBox.setX(nameCellX());
                    cellBox.setWidth(nameCellW());
                }
                cellBox.setY(y + 2);
            }
            y += ROW_H;
        }
    }

    private String countFor(int index) {
        String pattern = index < patterns.size() ? patterns.get(index) : "";
        if (!pattern.isEmpty() && !valid(pattern)) {
            return Component.translatable("screen.chatsphere.console_tabs.invalid").getString();
        }
        return Component.translatable("screen.chatsphere.console_tabs.matches",
                index < matches.length ? matches[index] : 0, totalMessages).getString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int closeX = width - PAD - 16;
        int closeY = (HEADER_H - 16) / 2;
        if (mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16) {
            onClose();
            return true;
        }
        if (editingRow >= 0 && cellBox != null && cellBox.visible) {
            boolean inside = mouseX >= cellBox.getX() && mouseX < cellBox.getX() + cellBox.getWidth()
                    && mouseY >= cellBox.getY() && mouseY < cellBox.getY() + cellBox.getHeight();
            if (!inside) commitEdit();
        }
        int y = HEADER_H + 6 - scrollOffset;
        for (int i = 0; i < tabs.size(); i++) {
            if (mouseY >= y && mouseY < y + ROW_H) {
                if (mouseX >= nameCellX() && mouseX < nameCellX() + nameCellW()) {
                    startEditing(i, false, nameCellX(), y, nameCellW());
                    return true;
                }
                int patternW = patternCellW(countRight() - font.width(countFor(i)));
                if (mouseX >= patternCellX() && mouseX < patternCellX() + patternW) {
                    startEditing(i, true, patternCellX(), y, patternW);
                    return true;
                }
            }
            y += ROW_H;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sy) {
        if (scrollMax <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) (sy * 20), 0, scrollMax);
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
