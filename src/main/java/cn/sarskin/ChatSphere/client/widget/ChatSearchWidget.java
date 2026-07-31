package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ChatSearchWidget {
    public boolean visible;
    private EditBox searchInput;
    private String query = "";
    private List<Integer> results = new ArrayList<>();
    private int resultIndex = -1;

    public void init(int x, int y, int width) {
        searchInput = new EditBox(Minecraft.getInstance().font, x, y, 160, 12,
                Component.translatable("screen.chatsphere.search.hint"));
        searchInput.setMaxLength(64);
        searchInput.setBordered(false);
        searchInput.setVisible(false);
        searchInput.setResponder(this::onSearchChanged);
    }

    public EditBox getInput() { return searchInput; }

    public void setVisible(boolean v) {
        visible = v;
        if (searchInput != null) searchInput.setVisible(v);
        if (!v) {
            query = "";
            results = List.of();
            resultIndex = -1;
        }
    }

    private void onSearchChanged(String q) {
        query = q;
        if (q.isEmpty()) {
            results = List.of();
            resultIndex = -1;
            return;
        }
        results = ChatHistoryManager.getInstance().searchMessages(
                ChatHistoryManager.getInstance().getConversationIds().isEmpty() ? null
                        : ChatHistoryManager.getInstance().getConversationIds().get(0), q);
        resultIndex = results.isEmpty() ? -1 : 0;
    }

    public void setConversation(String convId) {
        if (!query.isEmpty()) {
            results = ChatHistoryManager.getInstance().searchMessages(convId, query);
            resultIndex = results.isEmpty() ? -1 : 0;
        }
    }

    public int navigateNext() {
        if (results.isEmpty()) return -1;
        resultIndex = (resultIndex + 1) % results.size();
        return results.get(resultIndex);
    }

    public boolean keyPressed(int keyCode) {
        if (!visible || searchInput == null || !searchInput.isFocused()) return false;
        if (keyCode == 256) { setVisible(false); return true; }
        if (keyCode == 257 || keyCode == 335) {
            if (!results.isEmpty()) return true; // consumed but navigateNext called by caller
            return true;
        }
        return false;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, int sidebarWidth) {
        if (!visible || searchInput == null) return;
        searchInput.render(g, mouseX, mouseY, 0);
        var font = Minecraft.getInstance().font;

        int closeX = sidebarWidth + 166;
        g.fill(closeX, 2, closeX + 10, 14, Theme.isDark() ? 0xFF333333 : 0xFFDDDDDD);
        g.drawString(font, "X", closeX + 2, 2, 0xFFFF4444, false);

        if (!results.isEmpty()) {
            g.drawString(font, (resultIndex + 1) + "/" + results.size(), sidebarWidth + 178, 3, Theme.accent(), false);
        } else if (!query.isEmpty()) {
            g.drawString(font, Component.translatable("screen.chatsphere.search.no_match"), sidebarWidth + 178, 3, Theme.textDim(), false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible || searchInput == null || button != 0) return false;
        if (mouseX >= searchInput.getX() && mouseX <= searchInput.getX() + searchInput.getWidth()
                && mouseY >= searchInput.getY() && mouseY <= searchInput.getY() + searchInput.getHeight()) {
            return true;
        }
        return false;
    }
}
