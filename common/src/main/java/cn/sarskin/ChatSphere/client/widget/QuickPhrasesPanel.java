package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import static cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG_SPEC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class QuickPhrasesPanel {
    private static final int PANEL_W = 140;
    private static final int ROW_H = 16;
    private static final int VISIBLE_ROWS = 8;
    private static final int ADD_H = 16;
    private static final int PAD = 4;

    public boolean visible;
    private int scroll;
    private final EditBox addInput;
    private boolean addFocused;

    public QuickPhrasesPanel() {
        var font = Minecraft.getInstance().font;
        addInput = new EditBox(font, 0, 0, PANEL_W - PAD * 2 - 22, 12,
                Component.translatable("chatsphere.quick_phrases.add_hint"));
        addInput.setMaxLength(64);
        addInput.setBordered(false);
        addInput.setVisible(false);
    }

    private List<String> getPhrases() {
        return new ArrayList<>(ModClientConfig.CONFIG.quickPhrases.get());
    }

    private void savePhrases(List<String> list) {
        ModClientConfig.CONFIG.quickPhrases.set(list);
        CONFIG_SPEC.save();
    }

    public int panelH() {
        List<String> phrases = getPhrases();
        int listRows = Math.min(phrases.size(), VISIBLE_ROWS);
        return PAD + listRows * ROW_H + 8 + ADD_H + PAD;
    }

    public void render(GuiGraphics g, int panelX, int panelY, int mouseX, int mouseY) {
        if (!visible) return;
        var font = Minecraft.getInstance().font;
        List<String> phrases = getPhrases();
        int ph = panelH();

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + ph, Theme.panelBg());
        g.renderOutline(panelX, panelY, PANEL_W, ph, Theme.popupOutline());

        int listRows = Math.min(phrases.size(), VISIBLE_ROWS);
        int listH = listRows * ROW_H;
        int listTop = panelY + PAD;
        boolean hasScroll = phrases.size() > VISIBLE_ROWS;

        int textRight = panelX + PANEL_W - (hasScroll ? 8 : PAD);
        int textW = textRight - (panelX + PAD) - 14;

        if (hasScroll) {
            int sbX = panelX + PANEL_W - 6;
            int sbTop = listTop;
            int sbBot = listTop + listH;
            g.fill(sbX, sbTop, sbX + 3, sbBot, Theme.scrollTrack());
            int thumbH = Math.max(6, listH * VISIBLE_ROWS / phrases.size());
            int maxScroll = phrases.size() - VISIBLE_ROWS;
            int travel = listH - thumbH;
            int thumbY = sbTop + (maxScroll > 0 ? scroll * travel / maxScroll : 0);
            g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, Theme.scrollThumb());
        }

        int endIdx = Math.min(scroll + VISIBLE_ROWS, phrases.size());
        for (int i = scroll; i < endIdx; i++) {
            int rowY = listTop + (i - scroll) * ROW_H;
            String phrase = font.plainSubstrByWidth(phrases.get(i), textW);
            boolean hover = mouseX >= panelX + PAD && mouseX <= textRight
                    && mouseY >= rowY && mouseY <= rowY + ROW_H;
            if (hover) g.fill(panelX + PAD, rowY, textRight, rowY + ROW_H, Theme.menuHover());
            g.drawString(font, phrase, panelX + PAD + 2, rowY + 3, Theme.textMain(), false);

            int delX = textRight - 12;
            boolean hoverDel = mouseX >= delX && mouseX <= delX + 11
                    && mouseY >= rowY && mouseY <= rowY + ROW_H;
            g.fill(delX, rowY + 2, delX + 11, rowY + 13, hoverDel ? 0x66FF4444 : Theme.searchCloseBg());
            g.drawString(font, "\u2715", delX + 3, rowY + 3, 0xFFFF6666, false);
        }

        int addY = panelY + ph - ADD_H - PAD;
        g.fill(panelX + PAD, addY, panelX + PANEL_W - PAD, addY + ADD_H, Theme.inputBg());
        g.renderOutline(panelX + PAD, addY, PANEL_W - PAD * 2, ADD_H, Theme.divider());

        addInput.setX(panelX + PAD + 2);
        addInput.setY(addY + 2);
        addInput.setWidth(PANEL_W - PAD * 2 - 22);
        addInput.setVisible(true);
        addInput.render(g, mouseX, mouseY, 0);

        int addBtnX = panelX + PANEL_W - PAD - 18;
        boolean hoverAdd = mouseX >= addBtnX && mouseX <= addBtnX + 16
                && mouseY >= addY && mouseY <= addY + ADD_H;
        g.fill(addBtnX, addY, addBtnX + 16, addY + ADD_H, hoverAdd ? 0x66448888 : Theme.searchCloseBg());
        g.drawString(font, "+", addBtnX + 5, addY + 3, 0xFF88FF88, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int panelX, int panelY, EditBox mainInput) {
        if (!visible || button != 0) return false;
        var font = Minecraft.getInstance().font;
        List<String> phrases = getPhrases();
        int ph = panelH();

        if (mouseX < panelX || mouseX > panelX + PANEL_W || mouseY < panelY || mouseY > panelY + ph) {
            visible = false;
            addFocused = false;
            addInput.setFocused(false);
            mainInput.setFocused(true);
            return false;
        }

        int listRows = Math.min(phrases.size(), VISIBLE_ROWS);
        int listH = listRows * ROW_H;
        int listTop = panelY + PAD;
        boolean hasScroll = phrases.size() > VISIBLE_ROWS;
        int textRight = panelX + PANEL_W - (hasScroll ? 8 : PAD);

        int endIdx = Math.min(scroll + VISIBLE_ROWS, phrases.size());
        for (int i = scroll; i < endIdx; i++) {
            int rowY = listTop + (i - scroll) * ROW_H;

            int delX = textRight - 12;
            if (mouseX >= delX && mouseX <= delX + 11 && mouseY >= rowY && mouseY <= rowY + ROW_H) {
                var list = new ArrayList<>(phrases);
                list.remove(i);
                savePhrases(list);
                scroll = Math.min(scroll, Math.max(0, list.size() - VISIBLE_ROWS));
                return true;
            }

            if (mouseX >= panelX + PAD && mouseX <= textRight && mouseY >= rowY && mouseY <= rowY + ROW_H) {
                mainInput.setValue(phrases.get(i));
                mainInput.moveCursorToEnd();
                visible = false;
                addFocused = false;
                addInput.setFocused(false);
                mainInput.setFocused(true);
                return true;
            }
        }

        int addY = panelY + ph - ADD_H - PAD;
        int addBtnX = panelX + PANEL_W - PAD - 18;
        if (mouseX >= addBtnX && mouseX <= addBtnX + 16 && mouseY >= addY && mouseY <= addY + ADD_H) {
            String text = addInput.getValue().trim();
            if (!text.isEmpty()) {
                var list = new ArrayList<>(phrases);
                list.add(text);
                savePhrases(list);
                addInput.setValue("");
                addInput.setFocused(true);
            }
            return true;
        }

        if (mouseX >= panelX + PAD && mouseX <= panelX + PANEL_W - PAD
                && mouseY >= addY && mouseY <= addY + ADD_H) {
            addFocused = true;
            addInput.setFocused(true);
            mainInput.setFocused(false);
            return true;
        }

        addFocused = false;
        addInput.setFocused(false);
        mainInput.setFocused(true);
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!visible || !addFocused) return false;
        if (keyCode == 257 || keyCode == 335) {
            String text = addInput.getValue().trim();
            if (!text.isEmpty()) {
                var list = getPhrases();
                list.add(text);
                savePhrases(list);
                addInput.setValue("");
            }
            return true;
        }
        if (addInput.keyPressed(keyCode, scanCode, modifiers))
            return true;
        return addInput.canConsumeInput();
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!visible || !addFocused) return false;
        return addInput.charTyped(codePoint, modifiers);
    }

    public boolean mouseScrolled(double scrollY) {
        if (!visible) return false;
        int maxScroll = Math.max(0, ModClientConfig.CONFIG.quickPhrases.get().size() - VISIBLE_ROWS);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) scrollY));
        return true;
    }

    public void toggle() { visible = !visible; if (!visible) { addFocused = false; addInput.setFocused(false); } }
}
