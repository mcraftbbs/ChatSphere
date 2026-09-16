package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.ui.UiToggle;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import cn.sarskin.ChatSphere.server.ModServerChannels;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stepped channel creator: template, basics, sub-channels. */
public class CreateChannelScreen extends Screen {
    private static final int POPUP_WIDTH = 400;
    private static final int PAD = 16;
    private static final int STEP_COUNT = 3;
    private static final int MAX_SUBS = 6;
    private static final int TILE_W = 180;

    private final Screen parent;
    private EditBox nameInput;
    private EditBox descInput;
    private EditBox defaultSubInput;
    private EditBox customSubInput;
    private StyledButton nextBtn;
    private StyledButton addSubBtn;
    private final List<UiToggle> subToggles = new ArrayList<>();

    private int step;
    private String name = "";
    private String description = "";
    private String defaultSub = "";
    private boolean isPublic = true;
    private boolean mainChatEnabled = true;
    private int selectedTemplate = -1;
    /** Sub-channels suggested by the template, parallel to subEnabled. */
    private List<String> templateSubs = new ArrayList<>();
    private List<Boolean> subEnabled = new ArrayList<>();

    /** Layout metrics, compacted on short windows. */
    private int headerH;
    private int footerH;
    private int labelH;
    private int rowH;
    private int subRow;
    private int tileH;
    private int tileGap;
    private int blankH;
    private int fieldH;
    private int tailGap;
    private int maxSubs = MAX_SUBS;

    private int popupX;
    private int popupY;
    private int popupH;
    private int contentTop;
    private int contentRight;
    private int footerY;
    private int tilesY;

    public CreateChannelScreen(Screen parent) {
        super(Component.translatable("screen.chatsphere.create_channel.title"));
        this.parent = parent;
    }

    /** One tile of step 1: prefills only, nothing is created until confirm. */
    private record Template(String nameKey, String tipKey, String suggestion, String descKey, String[] subs) {}

    private static final Template BLANK = new Template(
            "screen.chatsphere.create_channel.blank", "screen.chatsphere.create_channel.blank_tip",
            "", "screen.chatsphere.create_channel.blank_desc", new String[]{});

    private static final Template[] TEMPLATES = {
            new Template("screen.chatsphere.create_channel.tpl_team", "screen.chatsphere.create_channel.tpl_team_tip",
                    "team", "screen.chatsphere.create_channel.tpl_team_desc",
                    new String[]{"screen.chatsphere.create_channel.sub_text", "screen.chatsphere.create_channel.sub_voice"}),
            new Template("screen.chatsphere.create_channel.tpl_trade", "screen.chatsphere.create_channel.tpl_trade_tip",
                    "trade", "screen.chatsphere.create_channel.tpl_trade_desc",
                    new String[]{"screen.chatsphere.create_channel.sub_sell", "screen.chatsphere.create_channel.sub_buy"}),
            new Template("screen.chatsphere.create_channel.tpl_chat", "screen.chatsphere.create_channel.tpl_chat_tip",
                    "chat", "screen.chatsphere.create_channel.tpl_chat_desc", new String[]{}),
            new Template("screen.chatsphere.create_channel.tpl_build", "screen.chatsphere.create_channel.tpl_build_tip",
                    "build", "screen.chatsphere.create_channel.tpl_build_desc",
                    new String[]{"screen.chatsphere.create_channel.sub_works"}),
    };

    private static List<String> resolveSubs(Template template) {
        List<String> out = new ArrayList<>();
        for (String key : template.subs()) out.add(Component.translatable(key).getString());
        return out;
    }

    private String stepTitleKey() {
        return switch (step) {
            case 0 -> "screen.chatsphere.create_channel.title";
            case 1 -> "screen.chatsphere.create_channel.step_basics";
            default -> "screen.chatsphere.create_channel.step_subs";
        };
    }

    private void readMetrics() {
        boolean compact = this.height < 300;
        headerH = compact ? 30 : 36;
        footerH = compact ? 38 : 44;
        labelH = compact ? 11 : 12;
        rowH = compact ? 22 : 30;
        subRow = compact ? 20 : 24;
        tileH = compact ? 36 : 46;
        tileGap = compact ? 6 : 8;
        blankH = compact ? 26 : 34;
        fieldH = compact ? 18 : 20;
        tailGap = compact ? 16 : 26;
    }

    @Override
    protected void init() {
        // Widgets are rebuilt per step; save what was typed first
        captureFields();
        nameInput = null;
        descInput = null;
        defaultSubInput = null;
        customSubInput = null;
        addSubBtn = null;
        subToggles.clear();

        readMetrics();
        int available = Math.max(150, this.height - 12);
        int step2Fixed = headerH + labelH + 14 + labelH + fieldH + 10 + footerH;
        maxSubs = Math.max(1, Math.min(MAX_SUBS, (available - step2Fixed) / subRow));

        popupH = switch (step) {
            case 0 -> headerH + labelH + (tileH + tileGap) * 2 + blankH + tileGap + 14 + footerH;
            case 1 -> headerH + labelH + fieldH + 8 + labelH + fieldH + rowH + rowH + rowH + tailGap + footerH;
            default -> headerH + labelH + Math.max(1, templateSubs.size()) * subRow + 14
                    + labelH + fieldH + 10 + footerH;
        };
        popupH = Math.min(popupH, available);
        popupX = (this.width - POPUP_WIDTH) / 2;
        popupY = Math.max(4, (this.height - popupH) / 2);
        contentTop = popupY + headerH;
        contentRight = popupX + POPUP_WIDTH - PAD;
        footerY = popupY + popupH - footerH + 12;

        if (step == 0) tilesY = contentTop + labelH + 2;
        else if (step == 1) buildStepBasics();
        else buildStepSubs();

        int btnW = 88;
        this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.create_channel.cancel"),
                btn -> cancel()
        ).bounds(popupX + PAD, footerY, btnW, 20).style(StyledButton.Style.CANCEL).tooltip(
                Component.translatable("screen.chatsphere.create_channel.tip_cancel")
        ).build());

        int nextX = contentRight - btnW;
        String nextKey = step == STEP_COUNT - 1
                ? "screen.chatsphere.create_channel.confirm" : "screen.chatsphere.create_channel.next";
        this.nextBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable(nextKey),
                btn -> next()
        ).bounds(nextX, footerY, btnW, 20).style(StyledButton.Style.CONFIRM).tooltip(
                Component.translatable(step == STEP_COUNT - 1
                        ? "screen.chatsphere.create_channel.tip_confirm"
                        : "screen.chatsphere.create_channel.tip_next")
        ).build());

        if (step > 0) {
            this.addRenderableWidget(StyledButton.styledBuilder(
                    Component.translatable("screen.chatsphere.create_channel.back"),
                    btn -> switchStep(step - 1)
            ).bounds(nextX - btnW - 8, footerY, btnW, 20).tooltip(
                    Component.translatable("screen.chatsphere.create_channel.tip_back")
            ).build());
        }
    }

    private void buildStepBasics() {
        int fieldX = popupX + PAD;
        int fieldW = POPUP_WIDTH - PAD * 2;
        int y = contentTop + labelH;

        this.nameInput = new EditBox(this.font, fieldX, y, fieldW, fieldH,
                Component.translatable("screen.chatsphere.create_channel.input_label"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setBordered(true);
        this.nameInput.setHint(Component.translatable("screen.chatsphere.create_channel.input_hint"));
        this.nameInput.setValue(name);
        this.addWidget(this.nameInput);
        this.setInitialFocus(this.nameInput);
        y += fieldH + 8 + labelH;

        this.descInput = new EditBox(this.font, fieldX, y, fieldW, fieldH,
                Component.translatable("screen.chatsphere.channel_config.description"));
        this.descInput.setMaxLength(64);
        this.descInput.setBordered(true);
        this.descInput.setHint(Component.translatable("screen.chatsphere.channel_config.description_hint"));
        this.descInput.setValue(description);
        this.addWidget(this.descInput);
        y += fieldH + (rowH - fieldH) + 4;

        this.addRenderableWidget(new UiToggle(contentRight - 60, y + 1, 60, 18, isPublic, v -> isPublic = v))
                .setTooltip(Tooltip.create(Component.translatable("screen.chatsphere.create_channel.tip_toggle_public")));
        y += rowH;

        this.addRenderableWidget(new UiToggle(contentRight - 60, y + 1, 60, 18, mainChatEnabled, v -> {
            mainChatEnabled = v;
            if (this.defaultSubInput != null) this.defaultSubInput.setVisible(!v);
        })).setTooltip(Tooltip.create(Component.translatable("screen.chatsphere.create_channel.tip_toggle_chat")));
        y += rowH;

        int subLabelW = Math.min(font.width(Component.translatable(
                "screen.chatsphere.channel_config.default_sub_label")) + 8, POPUP_WIDTH / 2);
        this.defaultSubInput = new EditBox(this.font, popupX + PAD + subLabelW, y,
                contentRight - (popupX + PAD + subLabelW), fieldH,
                Component.translatable("screen.chatsphere.channel_config.default_sub_hint"));
        this.defaultSubInput.setMaxLength(32);
        this.defaultSubInput.setBordered(true);
        this.defaultSubInput.setValue(defaultSub);
        this.defaultSubInput.setVisible(!mainChatEnabled);
        this.addWidget(this.defaultSubInput);
    }

    private void buildStepSubs() {
        int y = contentTop + labelH;
        for (int i = 0; i < templateSubs.size(); i++) {
            final int index = i;
            boolean on = i < subEnabled.size() && subEnabled.get(i);
            subToggles.add(this.addRenderableWidget(new UiToggle(
                    contentRight - 60, y + 2, 60, 18, on, v -> {
                        if (index < subEnabled.size()) subEnabled.set(index, v);
                    })));
            y += subRow;
        }
        if (templateSubs.isEmpty()) y += 14;

        int inputY = y + 10 + labelH;
        int addW = 56;
        this.customSubInput = new EditBox(this.font, popupX + PAD, inputY, POPUP_WIDTH - PAD * 2 - addW - 8, fieldH,
                Component.translatable("screen.chatsphere.create_channel.custom_sub"));
        this.customSubInput.setMaxLength(32);
        this.customSubInput.setBordered(true);
        this.customSubInput.setHint(Component.translatable("screen.chatsphere.create_channel.custom_sub_hint"));
        this.addWidget(this.customSubInput);
        this.addSubBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.create_channel.custom_sub_add"),
                btn -> addCustomSub()
        ).bounds(contentRight - addW, inputY, addW, fieldH).build());
    }

    private void addCustomSub() {
        if (customSubInput == null || templateSubs.size() >= maxSubs) return;
        String value = customSubInput.getValue().trim();
        if (!ModServerChannels.isValidChannelSegment(value)) return;
        for (String existing : templateSubs) {
            if (existing.equalsIgnoreCase(value)) return;
        }
        templateSubs.add(value);
        subEnabled.add(true);
        switchStep(step);
    }

    /** Moves between steps; typed values survive the rebuild. */
    private void switchStep(int target) {
        captureFields();
        step = Math.max(0, Math.min(STEP_COUNT - 1, target));
        clearWidgets();
        init();
    }

    private void captureFields() {
        if (nameInput != null) name = nameInput.getValue();
        if (descInput != null) description = descInput.getValue();
        if (defaultSubInput != null) defaultSub = defaultSubInput.getValue();
    }

    /** Templates only prefill the form; every field stays editable. */
    private void applyTemplate(int index) {
        Template template = index < 0 ? BLANK : TEMPLATES[index];
        selectedTemplate = index;
        name = template.suggestion();
        description = Component.translatable(template.descKey()).getString();
        templateSubs = resolveSubs(template);
        subEnabled = new ArrayList<>();
        for (int i = 0; i < templateSubs.size(); i++) subEnabled.add(true);
        if (mainChatEnabled) defaultSub = "";
    }

    /** Field text on the basics step, otherwise the value kept across steps. */
    private String plannedName() {
        String value = nameInput != null ? nameInput.getValue() : name;
        if (value == null) value = "";
        value = value.trim();
        if (value.startsWith("#")) value = value.substring(1);
        return value;
    }

    /** Same rule as the server, so a bad name is never sent. */
    private boolean nameValid() {
        return ModServerChannels.isValidChannelSegment(plannedName());
    }

    private void next() {
        if (step == 1 && !nameValid()) {
            // A silent refusal looked like a dead button; focus the name field
            if (nameInput != null) setFocused(nameInput);
            return;
        }
        if (step == STEP_COUNT - 1) {
            confirm();
            return;
        }
        switchStep(step + 1);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme.beginFrame();
        renderBackground(g, mouseX, mouseY, partialTick);

        if (nextBtn != null) nextBtn.active = step != 1 || nameValid();
        if (addSubBtn != null) addSubBtn.active = templateSubs.size() < maxSubs;

        int radius = Theme.cardRadius();
        Ui.fillRoundedRect(g, popupX, popupY, POPUP_WIDTH, popupH, radius, Theme.popupBg());
        if (Theme.popupBorderVisible()) {
            Ui.renderRoundedOutline(g, popupX, popupY, POPUP_WIDTH, popupH, radius, Theme.popupOutline());
        }

        drawHeader(g, mouseX, mouseY);
        if (step == 0) drawStepTemplates(g, mouseX, mouseY);
        else if (step == 1) drawStepBasics(g);
        else drawStepSubs(g);

        if (nameInput != null) nameInput.render(g, mouseX, mouseY, partialTick);
        if (descInput != null) descInput.render(g, mouseX, mouseY, partialTick);
        if (defaultSubInput != null) defaultSubInput.render(g, mouseX, mouseY, partialTick);
        if (customSubInput != null) customSubInput.render(g, mouseX, mouseY, partialTick);
        for (Renderable renderable : ((cn.sarskin.ChatSphere.mixin.ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        int iconX = popupX + PAD - 6;
        int iconY = popupY + 8;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.drawString(this.font, "#", iconX + (18 - this.font.width("#")) / 2, iconY + 5, Theme.accent(), false);
        g.drawString(this.font, Component.translatable(stepTitleKey()), iconX + 26, popupY + 12, Theme.text(), false);

        int closeX = contentRight - 8;
        int closeY = popupY + 9;
        boolean closeHover = isInside(mouseX, mouseY, closeX, closeY, 16, 16);
        if (closeHover) Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        g.drawString(this.font, "×", closeX + (16 - this.font.width("×")) / 2, closeY + 4,
                closeHover ? Theme.text() : Theme.textInactive(), false);

        int dotY = popupY + 17;
        int dotX = closeX - 14 - STEP_COUNT * 10;
        for (int i = 0; i < STEP_COUNT; i++) {
            Ui.fillRoundedRect(g, dotX + i * 10, dotY, 6, 6, 3, i == step ? Theme.accent() : Theme.sectionLine());
        }

        g.fill(popupX + PAD - 6, contentTop - 6, contentRight + 6, contentTop - 5, Theme.divider());
    }

    private void drawStepTemplates(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.tpl_label"),
                popupX + PAD, tilesY - labelH, Theme.textDim(), false);
        for (int i = -1; i < TEMPLATES.length; i++) {
            int tx = tileX(i);
            int ty = tileY(i);
            int tw = tileW(i);
            int th = tileH(i);
            boolean selected = i == selectedTemplate;
            boolean hover = isInside(mouseX, mouseY, tx, ty, tw, th);
            Ui.fillRoundedRect(g, tx, ty, tw, th, 5, selected || hover ? Theme.hoverRow() : Theme.iconBtnBg());
            if (selected) Ui.renderRoundedOutline(g, tx, ty, tw, th, 5, Theme.accent());
            Template template = i < 0 ? BLANK : TEMPLATES[i];
            int lineH = this.font.lineHeight;
            // Two lines only when the name and description both fit
            boolean twoLines = th >= 2 * lineH + 12;
            g.drawString(this.font, Component.translatable(template.nameKey()), tx + 8,
                    ty + (twoLines ? 8 : (th - lineH) / 2), selected ? Theme.accent() : Theme.text(), false);
            if (twoLines) {
                g.drawString(this.font, Component.translatable(template.descKey()), tx + 8, ty + 8 + lineH + 5,
                        Theme.textDim(), false);
                if (template.subs().length > 0 && th >= 3 * lineH + 12) {
                    g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.subs_count",
                            template.subs().length), tx + 8, ty + 8 + (lineH + 5) * 2, Theme.textFaint(), false);
                }
            }
        }
    }

    private int tileX(int index) {
        if (index < 0) return popupX + PAD;
        return popupX + PAD + (index % 2) * (TILE_W + tileGap);
    }

    private int tileY(int index) {
        if (index < 0) return tilesY + (tileH + tileGap) * 2;
        return tilesY + (index / 2) * (tileH + tileGap);
    }

    private int tileW(int index) {
        return index < 0 ? POPUP_WIDTH - PAD * 2 : TILE_W;
    }

    private int tileH(int index) {
        return index < 0 ? blankH : tileH;
    }

    private void drawStepBasics(GuiGraphics g) {
        g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.input_label"),
                popupX + PAD, nameInput.getY() - labelH, Theme.textDim(), false);
        g.drawString(this.font, Component.translatable("screen.chatsphere.channel_config.description"),
                popupX + PAD, descInput.getY() - labelH, Theme.textDim(), false);
        g.drawString(this.font, Component.translatable("screen.chatsphere.channel_config.public_label"),
                popupX + PAD, publicRowY() + 5, Theme.textDim(), false);
        g.drawString(this.font, Component.translatable("screen.chatsphere.channel_config.main_chat_label"),
                popupX + PAD, publicRowY() + rowH + 5, Theme.textDim(), false);
        if (!mainChatEnabled) {
            // Label sits on the field row so it cannot overlap the toggle above
            g.drawString(this.font, Component.translatable("screen.chatsphere.channel_config.default_sub_label"),
                    popupX + PAD, defaultSubInput.getY() + 5, Theme.textDim(), false);
        }

        String planned = plannedName();
        int previewY = footerY - 12;
        if (planned.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.preview_empty"),
                    popupX + PAD, previewY, Theme.textInactive(), false);
        } else if (!nameValid()) {
            g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.preview_invalid"),
                    popupX + PAD, previewY, 0xFFFF6666, false);
        } else {
            g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.preview",
                    "#" + planned), popupX + PAD, previewY, Theme.textDim(), false);
        }
    }

    /** Y of the public toggle row, below the description field. */
    private int publicRowY() {
        return descInput.getY() + fieldH + (rowH - fieldH) + 4;
    }

    private void drawStepSubs(GuiGraphics g) {
        g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.subs_label"),
                popupX + PAD, contentTop, Theme.textDim(), false);
        int y = contentTop + labelH;
        if (templateSubs.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.subs_none"),
                    popupX + PAD, y + 2, Theme.textInactive(), false);
        }
        for (int i = 0; i < templateSubs.size(); i++) {
            boolean on = i < subEnabled.size() && subEnabled.get(i);
            g.drawString(this.font, "#" + plannedName() + "/" + templateSubs.get(i),
                    popupX + PAD, y + (subRow - 8) / 2, on ? Theme.text() : Theme.textInactive(), false);
            y += subRow;
        }
        if (customSubInput != null) {
            g.drawString(this.font, Component.translatable("screen.chatsphere.create_channel.custom_sub"),
                    popupX + PAD, customSubInput.getY() - labelH, Theme.textDim(), false);
        }
    }

    private static boolean isInside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int closeX = contentRight - 8;
        if (isInside(mouseX, mouseY, closeX, popupY + 9, 16, 16)) {
            cancel();
            return true;
        }
        if (step == 0) {
            for (int i = -1; i < TEMPLATES.length; i++) {
                if (isInside(mouseX, mouseY, tileX(i), tileY(i), tileW(i), tileH(i))) {
                    applyTemplate(i);
                    switchStep(1);
                    return true;
                }
            }
        }
        // Widgets are dispatched by the screen itself
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (step > 0) switchStep(step - 1);
            else cancel();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            // On the sub-channel step Enter adds the custom sub-channel
            if (step == STEP_COUNT - 1 && customSubInput != null && customSubInput.isFocused()
                    && !customSubInput.getValue().trim().isEmpty()) {
                addCustomSub();
                return true;
            }
            next();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        if (!nameValid()) {
            switchStep(1);
            return;
        }
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        UUID ownerUuid = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getUUID() : null;
        String channelId = "#" + plannedName();
        String sub = mainChatEnabled ? "" : (defaultSubInput != null ? defaultSubInput.getValue().trim() : "");
        if (sub.contains("/")) sub = "";
        List<String> children = selectedSubs();

        if (ownerUuid != null && history.isServerConnected() && this.minecraft != null
                && this.minecraft.getConnection() != null) {
            var conn = this.minecraft.getConnection().getConnection();
            conn.send(createPayload(channelId, ownerUuid, sub));
            for (String child : children) {
                conn.send(createPayload(channelId + "/" + child, ownerUuid, ""));
            }
        } else {
            history.addChannel(channelId, ownerUuid);
            for (String child : children) {
                history.addChannel(channelId + "/" + child, ownerUuid);
            }
        }
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    private List<String> selectedSubs() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < templateSubs.size(); i++) {
            if (i < subEnabled.size() && subEnabled.get(i)) out.add(templateSubs.get(i));
        }
        return out;
    }

    private static net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket createPayload(
            String channelId, UUID ownerUuid, String defaultSub) {
        return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(
                        ServerboundChannelActionPayload.Action.CREATE,
                        channelId, ownerUuid, true, "", "",
                        List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "",
                        true, defaultSub));
    }

    private void cancel() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
