package cn.sarskin.ChatSphere.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class StyledButton extends Button {
    private Style style;

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

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int bgColor = style.bgColor;
        int hoverColor = style.hoverColor;
        int borderColor = style.borderColor;
        int textColor = style.textColor;

        if (!this.active) {
            bgColor = style.disabledBgColor;
            borderColor = style.disabledBorderColor;
            textColor = style.disabledTextColor;
        } else if (this.isHoveredOrFocused()) {
            bgColor = hoverColor;
        }

        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);
        guiGraphics.renderOutline(this.getX(), this.getY(), this.width, this.height, borderColor);

        var mcFont = Minecraft.getInstance().font;
        Component msg = this.getMessage();
        int tx = this.getX() + (this.width - mcFont.width(msg)) / 2;
        int ty = this.getY() + (this.height - 8) / 2;
        guiGraphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
        guiGraphics.drawString(mcFont, msg, tx, ty, textColor, false);
        guiGraphics.disableScissor();
    }

    public enum Style {
        DEFAULT(0xFF2A1A3E, 0xFF4A3A5E, 0xFF5A4A7E, 0xFFCCCCCC,
                0xFF3A3A3A, 0xFF555555, 0xFF666666),
        CONFIRM(0xFF1E5A32, 0xFF2E7A42, 0xFF3E9A52, 0xFFFFFFFF,
                0xFF2A3A2A, 0xFF3A5A3A, 0xFF666666),
        CANCEL(0xFF322A1E, 0xFF524A3E, 0xFF6A5A4E, 0xFFCCCCCC,
               0xFF3A3A3A, 0xFF555555, 0xFF666666),
        DANGER(0xFF5A1E1E, 0xFF7A3E3E, 0xFF9A4E4E, 0xFFFFFFFF,
               0xFF3A2A2A, 0xFF5A3A3A, 0xFF666666),
        TOGGLE_ON(0xFF285A28, 0xFF387A38, 0xFF489A48, 0xFFFFFFFF,
                0xFF2A3A2A, 0xFF3A5A3A, 0xFF666666),
        TOGGLE_OFF(0xFF5A2828, 0xFF7A3838, 0xFF9A4848, 0xFFFFFFFF,
                0xFF3A2A2A, 0xFF5A3A3A, 0xFF666666);

        final int bgColor;
        final int hoverColor;
        final int borderColor;
        final int textColor;
        final int disabledBgColor;
        final int disabledBorderColor;
        final int disabledTextColor;

        Style(int bg, int hover, int border, int text,
              int disabledBg, int disabledBorder, int disabledText) {
            this.bgColor = bg;
            this.hoverColor = hover;
            this.borderColor = border;
            this.textColor = text;
            this.disabledBgColor = disabledBg;
            this.disabledBorderColor = disabledBorder;
            this.disabledTextColor = disabledText;
        }
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

        public StyledButton build() {
            StyledButton btn = new StyledButton(x, y, width, height, message, onPress, style);
            btn.active = this.active;
            if (tooltip != null) {
                btn.setTooltip(Tooltip.create(tooltip));
            }
            return btn;
        }
    }
}
