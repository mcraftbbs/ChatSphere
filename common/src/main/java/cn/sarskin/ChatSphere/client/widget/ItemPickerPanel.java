package cn.sarskin.ChatSphere.client.widget;

import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.util.ItemSerialization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class ItemPickerPanel {
    public boolean visible;
    private static final int SLOTS_PER_ROW = 9;
    private static final int ROWS = 4;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_PAD = 2;
    private static final int PANEL_W = SLOTS_PER_ROW * (SLOT_SIZE + SLOT_PAD) + 6;
    private static final int PANEL_H = ROWS * (SLOT_SIZE + SLOT_PAD) + 6;
    private static final int OFFSET_ROWS = 3;

    public static final int ITEM_PICKER_PANEL_H = PANEL_H;

    public String selectedItemNbt;
    public ItemStack selectedItem = ItemStack.EMPTY;
    public int selectedSlotIndex = -1;
    private List<ItemStack> hotbarItems = new ArrayList<>();
    private List<ItemStack> inventoryItems = new ArrayList<>();
    private int hoveredSlot = -1;

    public void refresh() {
        hotbarItems.clear();
        inventoryItems.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Player p = mc.player;
        for (int i = 0; i < 9; i++) hotbarItems.add(p.getInventory().getItem(i));
        for (int i = 9; i < 36; i++) inventoryItems.add(p.getInventory().getItem(i));
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, int panelX, int panelY) {
        if (!visible) return;
        refresh();

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, Theme.panelBg());
        g.renderOutline(panelX, panelY, PANEL_W, PANEL_H, Theme.itemPickerOutline());

        int slotIndex = 0;
        hoveredSlot = -1;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int x = panelX + 3 + col * (SLOT_SIZE + SLOT_PAD);
                int y = panelY + 3 + row * (SLOT_SIZE + SLOT_PAD);
                ItemStack stack;
                if (row == 0) {
                    stack = slotIndex < hotbarItems.size() ? hotbarItems.get(slotIndex) : ItemStack.EMPTY;
                } else {
                    int invIdx = (row - 1) * SLOTS_PER_ROW + col;
                    stack = invIdx < inventoryItems.size() ? inventoryItems.get(invIdx) : ItemStack.EMPTY;
                }
                slotIndex++;

                boolean hovered = mouseX >= x && mouseX <= x + SLOT_SIZE && mouseY >= y && mouseY <= y + SLOT_SIZE;
                if (hovered) hoveredSlot = slotIndex - 1;
                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, hovered ? Theme.slotHover() : Theme.slotBg());
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x + 2, y + 2);
                    g.renderItemDecorations(Minecraft.getInstance().font, stack, x + 2, y + 2);
                }
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int panelX, int panelY) {
        if (!visible || button != 0) return false;
        int slotIndex = 0;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < SLOTS_PER_ROW; col++) {
                int x = panelX + 3 + col * (SLOT_SIZE + SLOT_PAD);
                int y = panelY + 3 + row * (SLOT_SIZE + SLOT_PAD);
                if (mouseX >= x && mouseX <= x + SLOT_SIZE && mouseY >= y && mouseY <= y + SLOT_SIZE) {
                    ItemStack stack;
                    int actualSlot;
                    if (row == 0) {
                        stack = slotIndex < hotbarItems.size() ? hotbarItems.get(slotIndex) : ItemStack.EMPTY;
                        actualSlot = slotIndex;
                    } else {
                        int invIdx = (row - 1) * SLOTS_PER_ROW + col;
                        stack = invIdx < inventoryItems.size() ? inventoryItems.get(invIdx) : ItemStack.EMPTY;
                        actualSlot = invIdx + 9;
                    }
                    if (!stack.isEmpty()) {
                        selectedItemNbt = ItemSerialization.serialize(stack);
                        selectedItem = stack.copy();
                        selectedSlotIndex = actualSlot;
                        visible = false;
                        return true;
                    }
                }
                slotIndex++;
            }
        }
        return false;
    }

    public void toggle() {
        visible = !visible;
        if (visible) refresh();
    }
}
