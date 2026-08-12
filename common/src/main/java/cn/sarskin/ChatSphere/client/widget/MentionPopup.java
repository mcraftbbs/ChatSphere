package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MentionPopup {
    public boolean visible;
    private List<String> candidates = new ArrayList<>();
    private int selectedIdx;

    public void update(String inputText, Map<String, PlayerInfo> onlinePlayers) {
        visible = false;
        int atIdx = inputText.lastIndexOf('@');
        if (atIdx < 0) return;
        String after = inputText.substring(atIdx + 1);
        if (after.contains(" ")) return;
        String filter = after.toLowerCase();
        candidates.clear();
        for (PlayerInfo info : onlinePlayers.values()) {
            String name = info.getProfile().getName();
            if (name.toLowerCase().contains(filter)) candidates.add(name);
        }
        if (candidates.isEmpty()) return;
        candidates.sort(String::compareToIgnoreCase);
        selectedIdx = 0;
        visible = true;
    }

    public void render(GuiGraphics g, EditBox input, int mouseX, int mouseY) {
        if (!visible || candidates.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        int popupX = input.getX();
        int popupH = Math.min(candidates.size(), 8) * font.lineHeight + 4;
        int popupY = input.getY() - popupH - 2;
        int maxW = 60;
        for (String name : candidates) maxW = Math.max(maxW, font.width(name));
        int popupW = maxW + 12;
        if (popupY < 20) popupY = input.getY() + input.getHeight() + 2;

        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, Theme.popupBg());
        g.renderOutline(popupX, popupY, popupW, popupH, Theme.popupOutline());

        int startIdx = Math.max(0, selectedIdx - 7);
        int endIdx = Math.min(candidates.size(), startIdx + 8);
        for (int i = startIdx; i < endIdx; i++) {
            int iy = popupY + 2 + (i - startIdx) * font.lineHeight;
            boolean sel = i == selectedIdx;
            if (sel) g.fill(popupX + 2, iy, popupX + popupW - 2, iy + font.lineHeight, Theme.menuHover());
            g.drawString(font, "@" + candidates.get(i), popupX + 4, iy + 1, sel ? 0xFFFFFF : 0xCCCCCC, false);
        }
    }

    public boolean keyPressed(int keyCode, EditBox input) {
        if (!visible) return false;
        if (keyCode == 258 || keyCode == 257 || keyCode == 335) {
            insertMention(input);
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
        int popupH = Math.min(candidates.size(), 8) * font.lineHeight + 4;
        int popupY = input.getY() - popupH - 2;
        int maxW = 60;
        for (String name : candidates) maxW = Math.max(maxW, font.width(name));
        int popupW = maxW + 12;
        if (popupY < 20) popupY = input.getY() + input.getHeight() + 2;

        if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + popupH) {
            int relY = (int) mouseY - popupY - 2;
            int idx = relY / font.lineHeight;
            idx += Math.max(0, selectedIdx - 7);
            if (idx >= 0 && idx < candidates.size()) {
                selectedIdx = idx;
                insertMention(input);
            }
            return true;
        }
        return false;
    }

    private void insertMention(EditBox input) {
        String text = input.getValue();
        int atIdx = text.lastIndexOf('@');
        if (atIdx >= 0) {
            input.setValue(text.substring(0, atIdx) + "@" + candidates.get(selectedIdx) + " ");
            input.moveCursorToEnd();
        }
        visible = false;
    }
}
