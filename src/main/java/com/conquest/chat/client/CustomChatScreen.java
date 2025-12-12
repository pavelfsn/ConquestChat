package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.config.ChatConfig;
import com.conquest.chat.enums.ChatMessageType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CustomChatScreen extends ChatScreen {

    private static final int TAB_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 4;

    private final long openTime;
    private float animationAlpha = 0.0f;
    private String currentTab;
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;

    private final ClientChatManager chatManager = ClientChatManager.getInstance();
    private CommandSuggestions customCommandSuggestions;

    // ХРАНИЛИЩЕ ЗОН КЛИКА (Очищается каждый кадр)
    private final List<ClickArea> clickAreas = new ArrayList<>();

    public CustomChatScreen(String initialMessage) {
        super(initialMessage);
        this.openTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        this.currentTab = chatManager.getActiveTabName();
        if (this.currentTab == null) {
            this.currentTab = "Общий";
            chatManager.setActiveTabName("Общий");
        }

        super.init();

        int chatWidth = ChatConfig.CLIENT.chatWidth.get();
        int chatHeight = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - chatHeight - 4;

        this.input.setX(x + 4);
        this.input.setY(y + chatHeight - 13);
        this.input.setWidth(chatWidth - 8);
        this.input.setBordered(false);
        this.input.setMaxLength(256);
        this.input.setTextColor(0xFFFFFFFF);

        this.customCommandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 0, 7, false, -805306368);
        this.customCommandSuggestions.setAllowSuggestions(true);
        this.customCommandSuggestions.updateCommandInfo();

        this.input.setResponder((text) -> {
            if (this.customCommandSuggestions != null) {
                this.customCommandSuggestions.updateCommandInfo();
            }
        });
    }

    @Override
    public void resize(net.minecraft.client.Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (this.customCommandSuggestions != null) {
            this.customCommandSuggestions.updateCommandInfo();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - openTime;
        float duration = ChatConfig.CLIENT.fadeDuration.get();
        this.animationAlpha = duration > 0 ? Mth.clamp(elapsed / duration, 0.0f, 1.0f) : 1.0f;

        int bgColor = 0xFF000000;
        int bgAlpha = 200;
        int finalBgColor = ((int)(bgAlpha * animationAlpha) << 24) | (bgColor & 0x00FFFFFF);

        int width = ChatConfig.CLIENT.chatWidth.get();
        int height = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - height - 4;

        graphics.fill(x, y, x + width, y + height, finalBgColor);
        graphics.fill(x, y, x + width, y + TAB_HEIGHT, 0xAA000000);

        renderTabs(graphics, mouseX, mouseY, x, y, width);

        graphics.enableScissor(x, y + TAB_HEIGHT + 2, x + width, y + height - 16);

        // Очищаем зоны клика перед рендером
        this.clickAreas.clear();
        renderMessages(graphics, mouseX, mouseY, x, y + TAB_HEIGHT + 2, width, height - TAB_HEIGHT - 16);

        graphics.disableScissor();

        renderSettingsButton(graphics, mouseX, mouseY, x, y);

        if (!currentTab.equals("Урон")) {
            int inputLineY = y + height - 16;
            graphics.fill(x, inputLineY, x + width, inputLineY + 1, 0x44FFFFFF);

            RenderSystem.setShaderColor(1f, 1f, 1f, animationAlpha);
            this.input.render(graphics, mouseX, mouseY, partialTick);

            if (this.customCommandSuggestions != null) {
                this.customCommandSuggestions.render(graphics, mouseX, mouseY);
            }
        } else {
            graphics.drawString(this.font, "Только чтение", x + 4, y + height - 12, 0xFFAAAAAA, false);
        }

        // Отрисовка Hover Tooltip (если мышь над зоной клика)
        Style hoveredStyle = getStyleAt(mouseX, mouseY);
        if (hoveredStyle != null && hoveredStyle.getHoverEvent() != null) {
            // ИСПРАВЛЕНИЕ: Вызываем метод у graphics, а не у this
            graphics.renderComponentHoverEffect(this.font, hoveredStyle, mouseX, mouseY);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        String[] tabs = {"Общий", "Торг", "Личное", "Урон"};
        int tabX = x + 24;
        int tabPadding = 8;
        for (String tab : tabs) {
            int textWidth = this.font.width(tab);
            boolean isActive = tab.equals(currentTab);
            int color = isActive ? 0xFFFFFFFF : 0xFFAAAAAA;
            graphics.drawString(this.font, Component.literal(tab), tabX, y + 4, color, false);
            if (isActive) {
                graphics.fill(tabX, y + TAB_HEIGHT - 2, tabX + textWidth, y + TAB_HEIGHT - 1, 0xFFFFFFFF);
            }
            tabX += textWidth + tabPadding * 2;
        }
    }

    private void renderMessages(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        List<Component> rawMessages = chatManager.getMessages(currentTab);
        if (rawMessages == null || rawMessages.isEmpty()) return;

        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        int maxTextWidth = width - 12 - SCROLLBAR_WIDTH;

        for (Component msg : rawMessages) {
            wrappedLines.addAll(this.font.split(msg, maxTextWidth));
        }

        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = height / lineHeight;
        int totalLines = wrappedLines.size();

        if (scrollOffset < 0) scrollOffset = 0;
        int maxScroll = Math.max(0, totalLines - visibleLines);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        int startIndex = Math.max(0, totalLines - visibleLines - scrollOffset);
        int endIndex = Math.max(0, totalLines - scrollOffset);

        int currentY = y + height - lineHeight;

        for (int i = endIndex - 1; i >= startIndex; i--) {
            if (i < 0 || i >= wrappedLines.size()) break;
            FormattedCharSequence line = wrappedLines.get(i);

            // Рисуем текст
            graphics.drawString(this.font, line, x + 4, currentY, 0xFFFFFFFF, false);

            // --- СОХРАНЯЕМ ЗОНЫ КЛИКА ---
            // Мы должны пройтись по компонентам строки, чтобы узнать, где какой стиль.
            // FormattedCharSequence - это "запеченный" текст, из него сложно достать стиль по координатам без Splitter.
            // Но мы можем использовать Splitter наоборот: перебрать символы? Нет, это медленно.

            // Лучший способ: использовать FontSplitter для получения стиля для всей ширины
            // Но так как мы не можем сохранить "пиксельную карту", мы сохраним ВСЮ строку как зону?
            // Нет, тогда клик будет неточным.

            // КОМПРОМИСС: Мы сохраняем координаты СТРОКИ. И проверяем стиль только внутри mouseClicked/render,
            // используя тот же метод getSplitter, НО с гарантированно правильными координатами Y.

            // Сохраняем зону строки для последующей проверки
            // (Это замена getStyleAt, но более надежная)
            // Но проблема getStyleAt была именно в поиске Y.
            // Здесь мы знаем Y точно: currentY.

            // Просто добавим "Виртуальную зону" для всей строки.
            // А в mouseClicked пройдемся по clickAreas.

            // Если мы используем старый getStyleAt, но с координатами из рендера?
            // Нет, clickAreas лучше.

            // Мы не можем сохранить стиль для каждого пикселя.
            // Но мы можем сохранить саму строку (FormattedCharSequence) и её Y.
            clickAreas.add(new ClickArea(x + 4, currentY, this.font.width(line), lineHeight, line));

            currentY -= lineHeight;
        }

        if (totalLines > visibleLines) {
            int scrollBarX = x + width - SCROLLBAR_WIDTH - 2;
            int trackHeight = height;
            int scrollH = Math.max(10, (int)((float)visibleLines / totalLines * trackHeight));
            float scrollP = (float)scrollOffset / (totalLines - visibleLines);
            int scrollY = y + trackHeight - scrollH - (int)(scrollP * (trackHeight - scrollH));

            graphics.fill(scrollBarX, y, scrollBarX + SCROLLBAR_WIDTH, y + trackHeight, 0x44000000);
            graphics.fill(scrollBarX, scrollY, scrollBarX + SCROLLBAR_WIDTH, scrollY + scrollH, 0xFFAAAAAA);
        }
    }

    // Новый метод получения стиля через сохраненные зоны
    private Style getStyleAt(double mouseX, double mouseY) {
        for (ClickArea area : clickAreas) {
            if (mouseY >= area.y && mouseY < area.y + area.height) {
                // Мы попали в строку по Y. Проверяем X.
                double relativeX = mouseX - area.x;
                if (relativeX >= 0 && relativeX <= area.width) {
                    return this.font.getSplitter().componentStyleAtWidth(area.line, (int)relativeX);
                }
            }
        }
        return null;
    }

    private void renderSettingsButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        graphics.drawString(this.font, Component.literal("⚙"), x + 4, y + 3, 0xFFAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.customCommandSuggestions != null && this.customCommandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int width = ChatConfig.CLIENT.chatWidth.get();
        int height = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - height - 4;

        int scrollBarX = x + width - SCROLLBAR_WIDTH - 2;
        if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + SCROLLBAR_WIDTH + 2 && mouseY >= y + TAB_HEIGHT && mouseY <= y + height - 16) {
            this.isDraggingScrollbar = true;
            updateScroll(mouseY, y, height);
            return true;
        }

        if (button == 0) {
            if (mouseX >= x + 4 && mouseX <= x + 12 && mouseY >= y + 3 && mouseY <= y + 11) {
                return true;
            }
            if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
                handleTabClick(mouseX, x, y);
                return true;
            }
        }

        // ПРОВЕРКА КЛИКА ПО СООБЩЕНИЮ (Используем новый метод)
        if (mouseY >= y + TAB_HEIGHT + 2 && mouseY <= y + height - 16) {
            Style style = getStyleAt(mouseX, mouseY);
            if (style != null && style.getClickEvent() != null) {
                if (button == 1) {
                    handleRightClick(style);
                    return true;
                } else if (button == 0) {
                    if (this.handleComponentClicked(style)) return true;
                }
            }
        }

        if (currentTab.equals("Урон")) return true;
        if (this.input.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ... (Остальные методы: mouseReleased, mouseDragged, updateScroll, handleRightClick, handleTabClick, keyPressed, sendMessageByTab, mouseScrolled) ...
    // ОСТАВЬТЕ ИХ БЕЗ ИЗМЕНЕНИЙ (или скопируйте из предыдущего файла)

    // Вспомогательный класс
    private static class ClickArea {
        final double x, y, width, height;
        final FormattedCharSequence line; // Храним саму строку

        ClickArea(double x, double y, double width, double height, FormattedCharSequence line) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.line = line;
        }
    }

    // (Ниже скопируйте методы updateScroll, handleRightClick, handleTabClick, keyPressed, sendMessageByTab, mouseScrolled)
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScrollbar) {
            int width = ChatConfig.CLIENT.chatWidth.get();
            int height = ChatConfig.CLIENT.chatHeight.get();
            int y = this.height - height - 4;
            updateScroll(mouseY, y, height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateScroll(double mouseY, int y, int height) {
        int messageAreaHeight = height - TAB_HEIGHT - 16;
        List<Component> rawMessages = chatManager.getMessages(currentTab);
        if (rawMessages == null || rawMessages.isEmpty()) return;

        int width = ChatConfig.CLIENT.chatWidth.get();
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        int maxTextWidth = width - 12 - SCROLLBAR_WIDTH;
        for (Component msg : rawMessages) wrappedLines.addAll(this.font.split(msg, maxTextWidth));

        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = messageAreaHeight / lineHeight;
        int totalLines = wrappedLines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);

        if (maxScroll > 0) {
            double trackTop = y;
            double trackHeight = messageAreaHeight;
            double relativeY = mouseY - trackTop;
            relativeY = Mth.clamp(relativeY, 0, trackHeight);
            double scrollH = Math.max(10, (visibleLines / (float)totalLines) * trackHeight);
            double availableSpace = trackHeight - scrollH;
            double ratio = (trackHeight - relativeY - (scrollH / 2)) / availableSpace;
            ratio = Mth.clamp(ratio, 0, 1);
            this.scrollOffset = (int)(ratio * maxScroll);
        }
    }

    private void handleRightClick(Style style) {
        ClickEvent event = style.getClickEvent();
        if (event != null && event.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            String val = event.getValue();
            ConquestChatMod.LOGGER.info("[DEBUG] Handling Right Click. Value: " + val);

            if (val.startsWith("/w ") || val.startsWith("@")) {
                String command = val.startsWith("@") ? "/w " + val.substring(1).trim() + " " : val;

                this.currentTab = "Личное";
                chatManager.setActiveTabName("Личное");
                this.scrollOffset = 0;
                this.input.setValue(command);
                this.input.setCursorPosition(command.length());
                this.input.setHighlightPos(command.length());
                this.input.setFocused(true);
                this.setFocused(this.input);
                if (this.customCommandSuggestions != null) this.customCommandSuggestions.updateCommandInfo();
            }
        }
    }

    private void handleTabClick(double mouseX, int x, int y) {
        String[] tabs = {"Общий", "Торг", "Личное", "Урон"};
        int tabX = x + 24;
        int tabPadding = 8;
        for (String tab : tabs) {
            int textWidth = this.font.width(tab);
            if (mouseX >= tabX && mouseX < tabX + textWidth + tabPadding * 2) {
                if (!currentTab.equals(tab)) {
                    this.currentTab = tab;
                    chatManager.setActiveTabName(tab);
                    this.scrollOffset = 0;
                    this.input.setValue("");
                    if (this.customCommandSuggestions != null) this.customCommandSuggestions.updateCommandInfo();
                }
                break;
            }
            tabX += textWidth + tabPadding * 2;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.customCommandSuggestions != null) {
            if (this.customCommandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = this.input.getValue().trim();
            if (!text.isEmpty()) {
                sendMessageByTab(text);
                this.minecraft.gui.getChat().addRecentChat(text);
                this.input.setValue("");
                if (!currentTab.equals("Личное")) this.minecraft.setScreen(null);
            } else {
                this.minecraft.setScreen(null);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendMessageByTab(String text) {
        ConquestChatMod.LOGGER.info("[DEBUG] Sending message. Tab: " + currentTab + ", Text: " + text);
        if (currentTab.equals("Торг")) {
            if (text.startsWith("/")) {
                this.minecraft.getConnection().sendCommand(text.substring(1));
            } else {
                this.minecraft.getConnection().sendChat("[Торг] " + text);
            }
        } else if (currentTab.equals("Личное")) {
            if (text.startsWith("/")) {
                this.minecraft.getConnection().sendCommand(text.substring(1));
            } else {
                ClientChatManager.getInstance().addMessage(ChatMessageType.GENERAL, Component.literal("§c[!] Во вкладке 'Личное' можно писать только команды (напр. /msg Nick)."));
            }
        } else if (currentTab.equals("Урон")) {
            // Block
        } else {
            if (text.startsWith("/")) {
                this.minecraft.getConnection().sendCommand(text.substring(1));
            } else {
                this.minecraft.getConnection().sendChat(text);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            this.scrollOffset += (delta > 0) ? 1 : -1;
            if (this.scrollOffset < 0) this.scrollOffset = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
