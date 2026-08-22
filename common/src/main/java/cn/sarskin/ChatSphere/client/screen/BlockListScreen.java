package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.mixin.ScreenAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Blocked players list; clicking a row also unblocks (legacy). */
public class BlockListScreen extends Screen {
    private static final ResourceLocation BLOCK_ICON = ResourceLocation.fromNamespaceAndPath(
            cn.sarskin.ChatSphere.ModInfo.MODID, "textures/gui/block.png");
    private static final int PAD = 12;
    private static final int HEADER_H = 36;
    private static final int ROW_H = 36;

    private final Screen parent;
    private int scrollOffset;
    private int scrollMax;
    private int unblockBtnX;
    private final List<String> blockedUuids = new ArrayList<>();
    private final List<String> blockedNames = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

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

        unblockBtnX = width - PAD - 70;

        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();
        for (int i = 0; i < blockedUuids.size(); i++) {
            final String uuid = blockedUuids.get(i);
            StyledButton btn = StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.blocklist.unblock"),
                b -> unblock(uuid)
            ).bounds(unblockBtnX, 0, 60, 20).style(StyledButton.Style.CONFIRM).tooltip(
                Component.translatable("screen.chatsphere.blocklist.tip_unblock")
            ).build();
            addActionWidget(btn);
        }

        addRenderableWidget(StyledButton.styledBuilder(
            CommonComponents.GUI_BACK,
            btn -> onClose()
        ).bounds(width - 10 - 8 - 100, height - 32, 100, 20).style(StyledButton.Style.CONFIRM).tooltip(
            Component.translatable("screen.chatsphere.blocklist.tip_back")
        ).build());
    }

    private void addActionWidget(AbstractWidget w) {
        scrollWidgets.add(w);
        addRenderableWidget(w);
    }

    private void unblock(String uuid) {
        ChatHistoryManager.getInstance().unblockPlayer(uuid);
        clearWidgets();
        init();
    }

    private void repositionButtons() {
        int y = HEADER_H - scrollOffset;
        for (int i = 0; i < scrollWidgets.size(); i++) {
            scrollWidgets.get(i).setY(y + (ROW_H - 20) / 2);
            y += ROW_H;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        scrollMax = Math.max(0, blockedUuids.size() * ROW_H - (height - HEADER_H - 40));
        scrollOffset = Mth.clamp(scrollOffset, 0, scrollMax);
        repositionButtons();

        int iconX = PAD;
        int iconY = (HEADER_H - 18) / 2;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.blit(BLOCK_ICON, iconX + 1, iconY + 1, 0, 0, 16, 16, 16, 16);

        Component title = Component.translatable("screen.chatsphere.blocklist.title");
        g.drawString(font, title, iconX + 26, (HEADER_H - 8) / 2, Theme.text(), false);

        Component count = Component.translatable("screen.chatsphere.blocklist.count", blockedUuids.size());
        g.drawString(font, count, width - PAD - 16 - 8 - font.width(count), (HEADER_H - 8) / 2, Theme.textDim(), false);

        int closeX = width - PAD - 16;
        int closeY = (HEADER_H - 16) / 2;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        g.drawString(font, "×", closeX + (16 - font.width("×")) / 2, closeY + 4,
            closeHover ? Theme.text() : Theme.textInactive(), false);

        g.fill(PAD, HEADER_H + 2, width - PAD, HEADER_H + 3, Theme.divider());

        if (blockedUuids.isEmpty()) {
            Component empty = Component.translatable("screen.chatsphere.blocklist.empty");
            g.drawString(font, empty, width / 2 - font.width(empty) / 2, HEADER_H + 40, Theme.textDim(), false);
        } else {
            int y = HEADER_H - scrollOffset;
            for (int i = 0; i < blockedUuids.size(); i++) {
                drawRow(g, blockedUuids.get(i), blockedNames.get(i), y, mouseX, mouseY);
                y += ROW_H;
            }
        }

        if (scrollMax > 0) {
            int trackTop = HEADER_H + 8;
            int trackBot = height - 8;
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

    private void drawRow(GuiGraphics g, String uuid, String name, int y, int mouseX, int mouseY) {
        int rowW = width - PAD * 2;
        boolean hovered = mouseX >= PAD && mouseX < width - PAD && mouseY >= y && mouseY < y + ROW_H;
        if (hovered) {
            Ui.fillRoundedRect(g, PAD, y, rowW, ROW_H, 6, Theme.hoverRow());
        }

        int avX = PAD + 4;
        int avY = y + (ROW_H - 20) / 2;
        drawPlayerHead(g, uuid, avX, avY, 20);

        int textX = avX + 20 + 10;
        g.drawString(font, name, textX, y + 4, Theme.textMain(), false);
        String uuidText = uuid.substring(0, Math.min(8, uuid.length()));
        g.drawString(font, uuidText, textX, y + 15, Theme.textFaint(), false);
    }

    private void drawPlayerHead(GuiGraphics g, String uuidStr, int x, int y, int size) {
        try {
            PlayerFaceRenderer.draw(g, PlayerSkinCache.getSkin(UUID.fromString(uuidStr)), x, y, size);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int closeX = width - PAD - 16;
            int closeY = (HEADER_H - 16) / 2;
            if (mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16) {
                onClose();
                return true;
            }
            // Row click unblocks (legacy convenience)
            int y = HEADER_H - scrollOffset;
            for (int i = 0; i < blockedUuids.size(); i++) {
                if (mouseX >= PAD && mouseX <= width - PAD && mouseY >= y && mouseY < y + ROW_H) {
                    unblock(blockedUuids.get(i));
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < PAD || mx > width - PAD || my < HEADER_H) return false;
        if (scrollMax <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) (sy * 20), 0, scrollMax);
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
