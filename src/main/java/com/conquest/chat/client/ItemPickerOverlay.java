package com.conquest.chat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import java.util.function.Consumer;

public final class ItemPickerOverlay {

    private static final int SLOT = 18; // размер слота (ванильный)
    private static final int PADDING = 6;
    private boolean open;
    private int selected = 0;
    private int x, y, w, h;
    private final List<Entry> entries = new ArrayList<>();

    // Tooltip cache (как в твоей исходной версии)
    private ItemStack cachedTooltipStack = ItemStack.EMPTY;
    private List<Component> cachedTooltipLines = List.of();
    private Optional<TooltipComponent> cachedTooltipImage = Optional.empty();

    public record Entry(int slotMain, ItemStack stack) {}

    /**
     * Информация о вставке предмета в input (диапазон в текущей строке + слот).
     * Метаданные живут отдельно от текста, никаких скрытых символов в строке нет.
     */
    public record InsertedItem(int start, int end, int slotMain, String placeholder) {}

    private Consumer<InsertedItem> onInsert = null;

    public void setOnInsert(Consumer<InsertedItem> onInsert) {
        this.onInsert = onInsert;
    }


    public boolean isOpen() {
        return open;
    }

    public void open(int chatX, int chatY, int chatW, int chatH, int screenW, int screenH) {
        if (open) {
            layout(chatX, chatY, chatW, chatH, screenW, screenH);
            return;
        }

        open = true;
        rebuild();
        selected = Mth.clamp(selected, 0, Math.max(0, entries.size() - 1));
        layout(chatX, chatY, chatW, chatH, screenW, screenH);
        resetTooltipCache();
    }

    public void toggle(int chatX, int chatY, int chatW, int chatH, int screenW, int screenH) {
        if (open) {
            close();
        } else {
            open(chatX, chatY, chatW, chatH, screenW, screenH);
        }
    }

    public void close() {
        open = false;
        resetTooltipCache();
    }

    public void layout(int chatX, int chatY, int chatW, int chatH, int screenW, int screenH) {
        int desiredW = 176;
        int desiredH = 110;

        int px = chatX + chatW + 10;
        int py = chatY;

        px = Math.min(px, screenW - desiredW - 4);
        py = Math.max(4, Math.min(py, screenH - desiredH - 4));

        this.x = px;
        this.y = py;
        this.w = desiredW;
        this.h = desiredH;
    }

    public void rebuild() {
        entries.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack st = inv.items.get(i);
            if (st.isEmpty()) continue;
            entries.add(new Entry(i, st.copy()));
        }
    }

    public boolean keyPressed(int keyCode) {
        if (!open) return false;

        return switch (keyCode) {
            case 263 -> { // LEFT
                move(-1);
                yield true;
            }
            case 262 -> { // RIGHT
                move(+1);
                yield true;
            }
            case 265 -> { // UP
                move(-9);
                yield true;
            }
            case 264 -> { // DOWN
                move(+9);
                yield true;
            }
            default -> false;
        };
    }

    private void move(int delta) {
        if (entries.isEmpty()) return;
        selected = Mth.clamp(selected + delta, 0, entries.size() - 1);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, EditBox input) {
        if (!open) return false;

        // клик по кнопке "Прикрепить"
        int btnX0 = x + PADDING;
        int btnY0 = y + h - 22;
        int btnX1 = x + w - PADDING;
        int btnY1 = y + h - 6;

        if (mouseX >= btnX0 && mouseX <= btnX1 && mouseY >= btnY0 && mouseY <= btnY1) {
            insertSelected(input);
            return true;
        }

        // клик по сетке
        int gridX = x + PADDING;
        int gridY = y + PADDING;
        int cols = 9;
        int rows = 4;
        int gridW = cols * SLOT;
        int gridH = rows * SLOT;

        if (mouseX >= gridX && mouseX < gridX + gridW && mouseY >= gridY && mouseY < gridY + gridH) {
            int cx = (int) ((mouseX - gridX) / SLOT);
            int cy = (int) ((mouseY - gridY) / SLOT);
            int idx = cy * cols + cx;

            if (idx >= 0 && idx < entries.size()) {
                selected = idx;
                if (button == 0) { // ЛКМ сразу вставляет
                    insertSelected(input);
                }
                return true;
            }
        }

        return false;
    }

    public void insertSelected(EditBox input) {

        if (!open || entries.isEmpty()) return;

        Entry e = entries.get(selected);

        String shownName = e.stack().getHoverName().getString();
        String placeholder = "[" + shownName + "]";

        String cur = input.getValue();
        int caret = input.getCursorPosition();

        String out = cur.substring(0, caret) + placeholder + cur.substring(caret);

        input.setValue(out);
        input.setCursorPosition(caret + placeholder.length());
        input.setHighlightPos(input.getCursorPosition());

        // Сообщаем экрану диапазон вставки, чтобы он вёл метаданные отдельно от текста.
        if (onInsert != null) {
            onInsert.accept(new InsertedItem(caret, caret + placeholder.length(), e.slotMain(), placeholder));
        }

    }

    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!open) return;

        Minecraft mc = Minecraft.getInstance();

        // фон
        g.fill(x, y, x + w, y + h, 0xAA000000);
        g.renderOutline(x, y, w, h, 0x55FFFFFF);

        // сетка
        int gridX = x + PADDING;
        int gridY = y + PADDING;
        int cols = 9;
        int rows = 4;

        int hoveredIndex = -1;
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int i = 0; i < cols * rows; i++) {
            int sx = gridX + (i % cols) * SLOT;
            int sy = gridY + (i / cols) * SLOT;

            int bg = (i == selected) ? 0x55FFFFFF : 0x22000000;
            g.fill(sx, sy, sx + SLOT, sy + SLOT, bg);
            g.renderOutline(sx, sy, SLOT, SLOT, 0x33000000);

            if (i < entries.size()) {
                ItemStack st = entries.get(i).stack();
                g.renderItem(st, sx + 1, sy + 1);
                g.renderItemDecorations(mc.font, st, sx + 1, sy + 1);

                if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
                    hoveredIndex = i;
                    hoveredStack = st;
                }
            }
        }

        // Tooltip
        if (hoveredIndex >= 0 && !hoveredStack.isEmpty()) {
            updateTooltipCache(mc, hoveredStack);
            g.renderTooltip(mc.font, cachedTooltipLines, cachedTooltipImage, mouseX, mouseY);
        }

        // кнопка
        int btnX0 = x + PADDING;
        int btnY0 = y + h - 22;
        int btnX1 = x + w - PADDING;
        int btnY1 = y + h - 6;
        g.fill(btnX0, btnY0, btnX1, btnY1, 0xFF222222);
        g.renderOutline(btnX0, btnY0, btnX1 - btnX0, btnY1 - btnY0, 0x55FFFFFF);
        g.drawCenteredString(mc.font, Component.literal("Прикрепить предмет"), (btnX0 + btnX1) / 2, btnY0 + 6, 0xFFFFFFFF);
    }

    private void resetTooltipCache() {
        cachedTooltipStack = ItemStack.EMPTY;
        cachedTooltipLines = List.of();
        cachedTooltipImage = Optional.empty();
    }

    private void updateTooltipCache(Minecraft mc, ItemStack hovered) {
        if (cachedTooltipStack.isEmpty() || !ItemStack.isSameItemSameTags(hovered, cachedTooltipStack)) {
            cachedTooltipStack = hovered.copy();
            cachedTooltipLines = Screen.getTooltipFromItem(mc, hovered);
            cachedTooltipImage = hovered.getTooltipImage();
        }
    }
}