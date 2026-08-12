package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.emoji.EmojiEntry;
import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class EmojiAutoComplete {
    public boolean visible;
    private List<EmojiEntry> candidates = new ArrayList<>();
    private int selectedIdx;
    private int colonIdx = -1;

    public void update(String inputText) {
        visible = false;
        int cursor = inputText.length();
        colonIdx = inputText.lastIndexOf(':', cursor - 1);
        if (colonIdx < 0 || colonIdx == cursor - 1) return;
        String after = inputText.substring(colonIdx + 1, cursor);
        if (after.contains(" ")) return;
        String filter = after.toLowerCase();
        candidates = EmojiRegistry.search(filter).stream()
                .filter(e -> !e.shortcode().equals(":" + filter + ":")
                        && e.shortcode().toLowerCase().contains(filter))
                .limit(12)
                .collect(java.util.stream.Collectors.toList());
        if (candidates.isEmpty()) return;
        selectedIdx = 0;
        visible = true;
    }

    public void render(GuiGraphics g, EditBox input, int mouseX, int mouseY) {
        if (!visible || candidates.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int popupX = input.getX();
        int popupH = Math.min(candidates.size(), 8) * (font.lineHeight + 2) + 4;
        int popupY = input.getY() - popupH - 2;
        int maxCodeW = 20;
        for (EmojiEntry e : candidates) {
            maxCodeW = Math.max(maxCodeW, font.width(e.shortcode()));
        }
        int popupW = font.lineHeight + maxCodeW + 20;
        if (popupY < 20) popupY = input.getY() + input.getHeight() + 2;

        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, Theme.popupBg());
        g.renderOutline(popupX, popupY, popupW, popupH, Theme.popupOutline());

        int startIdx = Math.max(0, Math.min(selectedIdx, candidates.size() - 8));
        int endIdx = Math.min(candidates.size(), startIdx + 8);
        for (int i = startIdx; i < endIdx; i++) {
            int iy = popupY + 2 + (i - startIdx) * (font.lineHeight + 2);
            boolean sel = i == selectedIdx;
            if (sel) g.fill(popupX + 2, iy, popupX + popupW - 2, iy + font.lineHeight + 2, Theme.menuHover());
            EmojiEntry e = candidates.get(i);
            String pua = EmojiRegistry.puaChar(e);
            if (pua != null) {
                g.drawString(font, Component.literal(pua).withStyle(EmojiRegistry.EMOJI_STYLE), popupX + 4, iy + 2 + EmojiRegistry.EMOJI_Y_OFFSET, 0xFFFFFF, false);
            } else {
                g.drawString(font, e.unicode(), popupX + 4, iy + 2, 0xFFFFFF, false);
            }
            g.drawString(font, e.shortcode(), popupX + font.lineHeight + 10, iy + 2, sel ? 0xFFFFAA : 0xCCCCCC, false);
        }
    }

    public boolean keyPressed(int keyCode, EditBox input) {
        if (!visible) return false;
        if (keyCode == 258 || keyCode == 257 || keyCode == 335) {
            insertEmoji(input);
            return true;
        }
        if (keyCode == 256) { visible = false; return true; }
        if (keyCode == 265) {
            selectedIdx = selectedIdx > 0 ? selectedIdx - 1 : candidates.size() - 1;
            return true;
        }
        if (keyCode == 264) {
            selectedIdx = selectedIdx < candidates.size() - 1 ? selectedIdx + 1 : 0;
            return true;
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, EditBox input) {
        if (!visible || button != 0) return false;
        var font = Minecraft.getInstance().font;
        int popupX = input.getX();
        int popupH = Math.min(candidates.size(), 8) * (font.lineHeight + 2) + 4;
        int popupY = input.getY() - popupH - 2;
        int maxCodeW = 20;
        for (EmojiEntry e : candidates) {
            maxCodeW = Math.max(maxCodeW, font.width(e.shortcode()));
        }
        int popupW = font.lineHeight + maxCodeW + 20;
        if (popupY < 20) popupY = input.getY() + input.getHeight() + 2;

        if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + popupH) {
            int relY = (int) mouseY - popupY - 2;
            int idx = relY / (font.lineHeight + 2);
            idx += Math.max(0, Math.min(selectedIdx, candidates.size() - 8));
            if (idx >= 0 && idx < candidates.size()) {
                selectedIdx = idx;
                insertEmoji(input);
            }
            return true;
        }
        return false;
    }

    private void insertEmoji(EditBox input) {
        String text = input.getValue();
        int cursor = input.getCursorPosition();
        if (colonIdx >= 0 && selectedIdx >= 0 && selectedIdx < candidates.size()) {
            EmojiEntry entry = candidates.get(selectedIdx);
            String insertion = entry.shortcode();
            input.setValue(text.substring(0, colonIdx) + insertion + text.substring(cursor));
            int newPos = colonIdx + insertion.length();
            input.setCursorPosition(newPos);
            input.setHighlightPos(newPos);
        }
        visible = false;
    }
}
