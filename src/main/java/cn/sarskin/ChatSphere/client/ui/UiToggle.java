package cn.sarskin.ChatSphere.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;

import java.util.function.Consumer;

public class UiToggle extends AbstractWidget {
    private boolean state;
    private final Consumer<Boolean> onToggle;

    public UiToggle(int x, int y, int w, int h, boolean initialState, Consumer<Boolean> onToggle) {
        super(x, y, w, h, CommonComponents.EMPTY);
        this.state = initialState;
        this.onToggle = onToggle;
    }

    public void setState(boolean state) {
        this.state = state;
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int trackH = Math.min(12, height - 4);
        int trackY = getY() + (height - trackH) / 2;
        int trackColor = state ? Theme.toggleOn() : Theme.toggleOff();
        Ui.fillRoundedRectStyle(g, 1, getX(), trackY, width, trackH, trackH / 2, trackColor);
        int knob = trackH - 4;
        int knobX = state ? getX() + width - knob - 2 : getX() + 2;
        Ui.fillRoundedRectStyle(g, 1, knobX, trackY + 2, knob, knob, knob / 2, Theme.toggleKnob());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered()) {
            state = !state;
            onToggle.accept(state);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
