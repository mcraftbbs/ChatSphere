package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class StyledButton extends Button {
    private Style style;
    /** Icon drawn instead of the label, tinted from the role colour; null means text only. */
    private net.minecraft.resources.ResourceLocation icon;
    private int iconSize = 10;

    public StyledButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = Style.DEFAULT;
    }

    public StyledButton(int x, int y, int width, int height, Component message, OnPress onPress, Style style) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.style = style;
    }

    public static Builder styledBuilder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    /** Draws the icon centred instead of the label; the message still feeds narration and tooltips. */
    public void setIcon(net.minecraft.resources.ResourceLocation icon, int size) {
        this.icon = icon;
        this.iconSize = size;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bgColor;
        int borderColor;
        int textColor;

        if (!this.active) {
            bgColor = style.getDisabledBgColor();
            borderColor = style.getDisabledBorderColor();
            textColor = style.getDisabledTextColor();
        } else {
            bgColor = this.isHoveredOrFocused() ? style.getHoverColor() : style.getBgColor();
            borderColor = style.getBorderColor();
            textColor = style.getTextColor();
        }

        int radius = Theme.buttonRadius();
        Ui.fillRoundedRect(guiGraphics, this.getX(), this.getY(), this.width, this.height, radius, bgColor);
        Ui.renderRoundedOutline(guiGraphics, this.getX(), this.getY(), this.width, this.height, radius, borderColor);

        var mcFont = Minecraft.getInstance().font;
        guiGraphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
        if (icon != null) {
            int ix = this.getX() + (this.width - iconSize) / 2;
            int iy = this.getY() + (this.height - iconSize) / 2;
            tint(guiGraphics, textColor);
            guiGraphics.blit(icon, ix, iy, 0, 0, iconSize, iconSize, iconSize, iconSize);
            tint(guiGraphics, 0xFFFFFFFF);
        } else {
            Component msg = this.getMessage();
            int tx = this.getX() + (this.width - mcFont.width(msg)) / 2;
            int ty = this.getY() + (this.height - 8) / 2;
            guiGraphics.drawString(mcFont, msg, tx, ty, textColor, false);
        }
        guiGraphics.disableScissor();
    }

    /** Applies a packed ARGB colour to the next blit; the atlas glyph itself is white. */
    private static void tint(GuiGraphics g, int argb) {
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    /** Colours come from {@link Theme} at draw time, so light mode and custom themes stay coherent. */
    public enum Style {
        DEFAULT(Role.NEUTRAL),
        CANCEL(Role.NEUTRAL),
        CONFIRM(Role.PRIMARY),
        DANGER(Role.DANGER),
        TOGGLE_ON(Role.PRIMARY),
        /** "Off" is a state, not a warning; a red one made whole member lists glow. */
        TOGGLE_OFF(Role.NEUTRAL);

        private enum Role { NEUTRAL, PRIMARY, DANGER }

        private final Role role;

        Style(Role role) {
            this.role = role;
        }

        private int baseBg() {
            return switch (role) {
                case PRIMARY -> Theme.buttonPrimaryBg();
                case DANGER -> Theme.buttonDangerBg();
                default -> Theme.buttonBg();
            };
        }

        public int getBgColor() { return baseBg(); }

        public int getHoverColor() { return Theme.buttonHover(baseBg()); }

        public int getBorderColor() { return Theme.buttonBorder(baseBg()); }

        public int getTextColor() {
            return switch (role) {
                case PRIMARY -> Theme.buttonPrimaryText();
                case DANGER -> Theme.buttonDangerText();
                default -> Theme.buttonText();
            };
        }

        public int getDisabledBgColor() { return Theme.buttonDisabledBg(baseBg()); }

        public int getDisabledBorderColor() { return Theme.buttonBorder(Theme.buttonDisabledBg(baseBg())); }

        public int getDisabledTextColor() { return Theme.textFaint(); }
    }

    public static class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private Style style = Style.DEFAULT;
        private boolean active = true;
        private Component tooltip;
        private net.minecraft.resources.ResourceLocation icon;
        private int iconSize = 10;

        Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) {
            this.x = x;
            this.y = y;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder style(Style style) {
            this.style = style;
            return this;
        }

        public Builder tooltip(Component tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        /** Replaces the label with a centred icon; the message still feeds narration. */
        public Builder icon(net.minecraft.resources.ResourceLocation icon, int size) {
            this.icon = icon;
            this.iconSize = size;
            return this;
        }

        public StyledButton build() {
            StyledButton btn = new StyledButton(x, y, width, height, message, onPress, style);
            btn.active = this.active;
            if (icon != null) {
                btn.setIcon(icon, iconSize);
            }
            if (tooltip != null) {
                btn.setTooltip(Tooltip.create(tooltip));
            }
            return btn;
        }
    }
}
