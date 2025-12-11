package com.conquest.chat.client;

import com.conquest.chat.config.ChatConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CustomChatScreen extends ChatScreen {

    private static final int TAB_HEIGHT = 16; // Высота шапки с вкладками
    private static final int SCROLLBAR_WIDTH = 2;

    private final long openTime;
    private float animationAlpha = 0.0f;
    private String currentTab = "Общий";
    private int scrollOffset = 0;

    private final ClientChatManager chatManager = ClientChatManager.getInstance();

    // Для автодополнения (TAB)
    private CommandSuggestions commandSuggestions;

    public CustomChatScreen(String initialMessage) {
        super(initialMessage);
        this.openTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init(); // Инициализирует this.input

        int chatWidth = ChatConfig.CLIENT.chatWidth.get();
        int chatHeight = ChatConfig.CLIENT.chatHeight.get();
        int x = 4; // Прижат к левому краю
        int y = this.height - chatHeight - 4;

        // Настройка поля ввода
        // Делаем его невидимым (без фона), но функциональным
        this.input.setX(x + 4);
        this.input.setY(y + chatHeight - 14);
        this.input.setWidth(chatWidth - 8);
        this.input.setBordered(false);
        this.input.setMaxLength(256);

        // Инициализация подсказок команд
        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 0, 10, false, -805306368);
        this.commandSuggestions.setAllowSuggestions(true);
        this.commandSuggestions.updateCommandInfo();

        // Логика авто-вставки символов при открытии
        if (currentTab.equals("Личные") && this.input.getValue().isEmpty()) {
            this.input.setValue("@");
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        String s = this.input.getValue();
        this.init(minecraft, width, height);
        this.input.setValue(s);
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Анимация
        long elapsed = System.currentTimeMillis() - openTime;
        float duration = ChatConfig.CLIENT.fadeDuration.get();
        this.animationAlpha = duration > 0 ? Mth.clamp(elapsed / duration, 0.0f, 1.0f) : 1.0f;

        int bgColor = 0xFF000000; // Черный фон
        int bgAlpha = 180; // Полупрозрачность (0-255)
        int finalBgColor = ((int)(bgAlpha * animationAlpha) << 24) | (bgColor & 0x00FFFFFF);

        int width = ChatConfig.CLIENT.chatWidth.get();
        int height = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - height - 4;

        // Фон всего окна
        graphics.fill(x, y, x + width, y + height, finalBgColor);

        // Верхняя панель (Header) темнее
        graphics.fill(x, y, x + width, y + TAB_HEIGHT, 0x88000000);

        // Элементы
        renderTabs(graphics, mouseX, mouseY, x, y, width);
        renderMessages(graphics, mouseX, mouseY, x, y + TAB_HEIGHT + 4, width, height - TAB_HEIGHT - 18);
        renderSettingsButton(graphics, mouseX, mouseY, x + 4, y + 4);

        // Input field render (custom)
        // Рисуем линию разделителя перед инпутом
        int inputLineY = y + height - 16;
        graphics.fill(x, inputLineY, x + width, inputLineY + 1, 0x44FFFFFF);

        // Отрисовка текста ввода вручную, если нужно кастомизировать цвет
        // Но super.render уже рисует input, если он visible.
        // Мы сделали setBordered(false), так что он рисует только текст.

        RenderSystem.setShaderColor(1f, 1f, 1f, animationAlpha);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.commandSuggestions.render(graphics, mouseX, mouseY);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        String[] tabs = {"Общий", "Торг", "Личные", "Урон", "+"}; // + как заглушка
        int tabX = x + 24; // Отступ под шестеренку
        int tabPadding = 6;

        int activeColor = 0xFFFFFFFF; // Белый
        int inactiveColor = 0xFFAAAAAA; // Серый

        for (String tab : tabs) {
            int textWidth = this.font.width(tab);
            boolean isActive = tab.equals(currentTab);

            // Если вкладка активна, рисуем под ней линию или фон
            if (isActive) {
                graphics.fill(tabX - 2, y + TAB_HEIGHT - 2, tabX + textWidth + 2, y + TAB_HEIGHT, 0xFFFFFFFF);
            }

            int color = isActive ? activeColor : inactiveColor;
            graphics.drawString(this.font, Component.literal(tab), tabX, y + 4, color, false);

            tabX += textWidth + tabPadding * 2;
        }
    }

    private void renderMessages(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        List<Component> rawMessages = chatManager.getMessages(currentTab);
        if (rawMessages == null || rawMessages.isEmpty()) return;

        // 1. Разбивка длинных строк (Wrapping)
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        int maxTextWidth = width - 12; // Отступ для скролла

        for (Component msg : rawMessages) {
            List<FormattedCharSequence> lines = this.font.split(msg, maxTextWidth);
            wrappedLines.addAll(lines);
        }

        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = height / lineHeight;
        int totalLines = wrappedLines.size();

        int startIndex = Math.max(0, totalLines - visibleLines - scrollOffset);
        int endIndex = Math.max(0, totalLines - scrollOffset);

        int currentY = y + height - lineHeight; // Рисуем снизу вверх

        for (int i = endIndex - 1; i >= startIndex; i--) {
            if (i < 0 || i >= wrappedLines.size()) break;
            FormattedCharSequence line = wrappedLines.get(i);
            graphics.drawString(this.font, line, x + 4, currentY, 0xFFFFFFFF, false);
            currentY -= lineHeight;
        }

        // Скроллбар
        if (totalLines > visibleLines) {
            int scrollH = Math.max(10, (int)((float)visibleLines / totalLines * height));
            float scrollP = (float)scrollOffset / (totalLines - visibleLines);
            int scrollY = y + height - scrollH - (int)(scrollP * (height - scrollH));

            // Левый скроллбар (как на скрине) или правый. Сделаем слева как на скрине STALCRAFT?
            // На скрине он слева.
            int scrollX = x + 1;
            graphics.fill(scrollX, scrollY, scrollX + SCROLLBAR_WIDTH, scrollY + scrollH, 0xFFFFFFFF);
        }
    }

    private void renderSettingsButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        graphics.drawString(this.font, Component.literal("⚙"), x, y + 4, 0xFFAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.commandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0) {
            int width = ChatConfig.CLIENT.chatWidth.get();
            int x = 4;
            int y = this.height - ChatConfig.CLIENT.chatHeight.get() - 4;

            // Вкладки
            if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
                String[] tabs = {"Общий", "Торг", "Личные", "Урон"};
                int tabX = x + 24;
                int tabPadding = 6;
                for (String tab : tabs) {
                    int textWidth = this.font.width(tab);
                    if (mouseX >= tabX && mouseX < tabX + textWidth + tabPadding * 2) {
                        if (!currentTab.equals(tab)) {
                            this.currentTab = tab;
                            this.scrollOffset = 0;
                            // Логика авто-вставки для Личных
                            if (tab.equals("Личные")) {
                                this.input.setValue("@");
                            } else {
                                if (this.input.getValue().equals("@")) this.input.setValue("");
                            }
                        }
                        return true;
                    }
                    tabX += textWidth + tabPadding * 2;
                }
            }

            // Настройки
            if (mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + TAB_HEIGHT) {
                Minecraft.getInstance().setScreen(null); // Закрыть чат (или открыть настройки)
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // Enter - отправка
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = this.input.getValue().trim();
            if (!text.isEmpty()) {
                sendMessageByTab(text);
                this.input.setValue("");
                // Не закрываем чат, если это "Личные", чтобы удобно было продолжать
                if (!currentTab.equals("Личные")) {
                    this.minecraft.setScreen(null);
                }
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendMessageByTab(String text) {
        // Логика маршрутизации
        if (currentTab.equals("Торг")) {
            // Если на сервере есть команда для торга (напр /trade)
            // this.minecraft.getConnection().sendCommand("trade " + text);
            // Если просто в чат с префиксом:
            this.minecraft.getConnection().sendChat("[Торг] " + text);
        } else if (currentTab.equals("Личные")) {
            // Ожидаем формат "@Nick message"
            if (text.startsWith("@")) {
                String[] parts = text.split(" ", 2);
                if (parts.length > 1) {
                    String target = parts[0].substring(1);
                    String msg = parts[1];
                    this.minecraft.getConnection().sendCommand("msg " + target + " " + msg);
                }
            } else {
                // Если забыл @
                this.minecraft.getConnection().sendChat(text);
            }
        } else if (currentTab.equals("Урон")) {
            // В чат урона обычно писать нельзя
            Minecraft.getInstance().getToasts().addToast(new SystemToast(
                    SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                    Component.literal("Ошибка"),
                    Component.literal("Нельзя писать в канал урона")
            ));
        } else {
            // Общий
            this.minecraft.getConnection().sendChat(text);
        }

        // Сразу отображаем у себя (фейк), пока сервер не ответит (для отзывчивости)
        chatManager.addMessage(com.conquest.chat.enums.ChatMessageType.GENERAL,
                Component.literal(minecraft.player.getName().getString() + ": " + text));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0) {
            this.scrollOffset += (delta > 0) ? 1 : -1;
            // Здесь нужно знать точное количество wrapped lines для правильного клэмпа
            // Упрощенно:
            if (this.scrollOffset < 0) this.scrollOffset = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
