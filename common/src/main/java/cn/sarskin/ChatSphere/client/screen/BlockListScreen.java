package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BlockListScreen extends Screen {
    private static final int ROW_H = 28;
    private static final int CONTENT_Y = 38;

    private final Screen parent;
    private int scrollOffset;
    private final List<String> blockedUuids = new ArrayList<>();
    private final List<String> blockedNames = new ArrayList<>();

    public BlockListScreen(Screen parent) {
        super(Component.translatable("screen.chatsphere.blocklist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        scrollOffset = 0;
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        blockedUuids.clear();
        blockedNames.clear();
        for (String uuid : history.getBlockedPlayers()) {
            blockedUuids.add(uuid);
            String name = history.getPlayerName(uuid);
            if (name == null) name = uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
            blockedNames.add(name);
        }

        addRenderableWidget(Button.builder(
            CommonComponents.GUI_BACK,
            btn -> onClose()
        ).bounds(width / 2 - 100, height - 32, 200, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, Theme.text(), false);

        if (blockedUuids.isEmpty()) {
            Component empty = Component.translatable("screen.chatsphere.blocklist.empty");
            g.drawString(font, empty, width / 2 - font.width(empty) / 2, CONTENT_Y + 20, Theme.textDim(), false);
            return;
        }

        int y = CONTENT_Y - scrollOffset;
        for (int i = 0; i < blockedUuids.size(); i++) {
            if (y < CONTENT_Y - ROW_H || y > height) { y += ROW_H; continue; }
            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + ROW_H;
            if (hovered) g.fill(10, y, width - 10, y + ROW_H, Theme.hoverRow());
            g.drawString(font, blockedNames.get(i), 14, y + 6, Theme.textMain(), false);
            String uuidText = blockedUuids.get(i).substring(0, Math.min(8, blockedUuids.get(i).length()));
            g.drawString(font, uuidText, 14, y + 16, Theme.textFaint(), false);
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = CONTENT_Y - scrollOffset;
            for (int i = 0; i < blockedUuids.size(); i++) {
                if (mouseX >= 10 && mouseX <= width - 10 && mouseY >= y && mouseY < y + ROW_H) {
                    ChatHistoryManager history = ChatHistoryManager.getInstance();
                    history.unblockPlayer(blockedUuids.get(i));
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int maxScroll = Math.max(0, blockedUuids.size() * ROW_H - (height - CONTENT_Y - 40));
        if (maxScroll <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * 20), 0, maxScroll);
        return true;
    }


    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
