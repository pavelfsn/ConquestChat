package com.conquest.chat.client;

import com.conquest.chat.config.ChatConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
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

    private static final int TAB_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 2;

    private final long openTime;
    private float animationAlpha = 0.0f;
    private String currentTab = "Общий";
    private int scrollOffset = 0;

    private final ClientChatManager chatManager = ClientChatManager.getInstance();
    private CommandSuggestions commandSuggestions;

    public CustomChatScreen(String initialMessage) {
        super(initialMessage);
        this.openTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        // Сначала super.init() чтобы создать this.input
        super.init();

        int chatWidth = ChatConfig.CLIENT.chatWidth.get();
        int chatHeight = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - chatHeight - 4;

        // Настройка поля ввода
        this.input.setX(x + 4);
        this.input.setY(y + chatHeight - 14);
        this.input.setWidth(chatWidth - 8);
        this.input.setBordered(false);
        this.input.setMaxLength(256);
        // Делаем текст белым всегда
        this.input.setTextColor(0xFFFFFFFF);

        // Важно: пересоздаем suggestions с правильными параметрами
        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 0, 10, false, -805306368);
        this.commandSuggestions.setAllowSuggestions(true);
        this.commandSuggestions.updateCommandInfo();

        // Авто-вставка @ для Личных
        if (currentTab.equals("Личные") && (this.input.getValue().isEmpty() || this.input.getValue().equals("/"))) {
            this.input.setValue("@");
            // Перемещаем курсор в конец, чтобы работали подсказки
            this.input.setCursorPosition(1);
            this.input.setHighlightPos(1);
            this.commandSuggestions.updateCommandInfo(); // Обновить подсказки сразу
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
        long elapsed = System.currentTimeMillis() - openTime;
        float duration = ChatConfig.CLIENT.fadeDuration.get();
        this.animationAlpha = duration > 0 ? Mth.clamp(elapsed / duration, 0.0f, 1.0f) : 1.0f;

        int bgColor = 0xFF000000;
        int bgAlpha = 180;
        int finalBgColor = ((int)(bgAlpha * animationAlpha) << 24) | (bgColor & 0x00FFFFFF);

        int width = ChatConfig.CLIENT.chatWidth.get();
        int height = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - height - 4;

        // Фон
        graphics.fill(x, y, x + width, y + height, finalBgColor);
        // Хедер
        graphics.fill(x, y, x + width, y + TAB_HEIGHT, 0x88000000);

        // Вкладки и контент
        renderTabs(graphics, mouseX, mouseY, x, y, width);
        renderMessages(graphics, mouseX, mouseY, x, y + TAB_HEIGHT + 4, width, height - TAB_HEIGHT - 18);
        renderSettingsButton(graphics, mouseX, mouseY, x + 4, y + 4);

        // Линия ввода
        int inputLineY = y + height - 16;
        graphics.fill(x, inputLineY, x + width, inputLineY + 1, 0x44FFFFFF);

        // ВАЖНО: Рисуем input вручную или через super, но контролируем альфу
        RenderSystem.setShaderColor(1f, 1f, 1f, animationAlpha);

        // Input рисуем сами, чтобы убрать стандартный черный фон если он пролезает
        // super.render(graphics, mouseX, mouseY, partialTick); // Можно убрать если мешает
        this.input.render(graphics, mouseX, mouseY, partialTick);

        // Подсказки рисуем ПОВЕРХ всего
        this.commandSuggestions.render(graphics, mouseX, mouseY);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        String[] tabs = {"Общий", "Торг", "Личные", "Урон", "+"};
        int tabX = x + 24;
        int tabPadding = 6;

        for (String tab : tabs) {
            int textWidth = this.font.width(tab);
            boolean isActive = tab.equals(currentTab);

            if (isActive) {
                graphics.fill(tabX - 2, y + TAB_HEIGHT - 2, tabX + textWidth + 2, y + TAB_HEIGHT, 0xFFFFFFFF);
            }

            int color = isActive ? 0xFF000000 : 0xFFAAAAAA; // Активный текст черный (на белом фоне)
            graphics.drawString(this.font, Component.literal(tab), tabX, y + 4, color, false);

            tabX += textWidth + tabPadding * 2;
        }
    }

    private void renderMessages(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        List<Component> rawMessages = chatManager.getMessages(currentTab);
        if (rawMessages == null || rawMessages.isEmpty()) return;

        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        int maxTextWidth = width - 12;

        for (Component msg : rawMessages) {
            wrappedLines.addAll(this.font.split(msg, maxTextWidth));
        }

        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = height / lineHeight;
        int totalLines = wrappedLines.size();

        int startIndex = Math.max(0, totalLines - visibleLines - scrollOffset);
        int endIndex = Math.max(0, totalLines - scrollOffset);

        int currentY = y + height - lineHeight;

        for (int i = endIndex - 1; i >= startIndex; i--) {
            if (i < 0 || i >= wrappedLines.size()) break;
            graphics.drawString(this.font, wrappedLines.get(i), x + 4, currentY, 0xFFFFFFFF, false);
            currentY -= lineHeight;
        }

        if (totalLines > visibleLines) {
            int scrollH = Math.max(10, (int)((float)visibleLines / totalLines * height));
            float scrollP = (float)scrollOffset / (totalLines - visibleLines);
            int scrollY = y + height - scrollH - (int)(scrollP * (height - scrollH));

            // Скролл слева
            graphics.fill(x, scrollY, x + SCROLLBAR_WIDTH, scrollY + scrollH, 0xFFFFFFFF);
        }
    }

    private void renderSettingsButton(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        graphics.drawString(this.font, Component.literal("⚙"), x + 4, y + 4, 0xFFAAAAAA, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.commandSuggestions.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0) {
            int width = ChatConfig.CLIENT.chatWidth.get();
            int height = ChatConfig.CLIENT.chatHeight.get();
            int x = 4;
            int y = this.height - height - 4;

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

                            // Логика переключения инпута
                            this.input.setValue(""); // Очищаем при смене
                            if (tab.equals("Личные")) {
                                this.input.setValue("@");
                            }
                        }
                        return true;
                    }
                    tabX += textWidth + tabPadding * 2;
                }
            }

            // Настройки
            if (mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + TAB_HEIGHT) {
                // Open config screen here
                return true;
            }

            // Клик по ссылкам (в области сообщений)
            if (mouseX >= x && mouseX <= x + width && mouseY >= y + TAB_HEIGHT && mouseY <= y + height - 16) {
                // Здесь нужна реализация getStyleAt, аналогичная предыдущей версии, но для wrappedLines
                // Это сложно сделать точно без сохранения структуры строк.
                // Пока оставим пустым, чтобы не крашить.
            }
        }

        // Даем инпуту фокус
        if (this.input.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 1. Сначала даем шанс подсказкам (TAB, Стрелки)
        if (this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // 2. Обработка Enter
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = this.input.getValue().trim();
            if (!text.isEmpty()) {
                sendMessageByTab(text);
                // Очищаем и закрываем, только если это не Личные (в личных удобно спамить)
                this.input.setValue(currentTab.equals("Личные") ? "@" : "");

                // ВАЖНО: Добавляем в историю (стрелочка вверх)
                this.minecraft.gui.getChat().addRecentChat(text);

                if (!currentTab.equals("Личные")) {
                    this.minecraft.setScreen(null);
                }
            } else {
                this.minecraft.setScreen(null);
            }
            return true;
        }

        // 3. Передаем нажатие в поле ввода
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendMessageByTab(String text) {
        if (currentTab.equals("Торг")) {
            // Либо команда, либо префикс
            this.minecraft.getConnection().sendChat("[Торг] " + text);
        } else if (currentTab.equals("Личные")) {
            if (text.startsWith("@")) {
                // Пытаемся распарсить "@Nick message"
                // Если пробела нет, значит сообщение пустое
                if (!text.contains(" ")) {
                    return; // Не отправляем огрызок
                }
                String[] parts = text.split(" ", 2);
                String target = parts[0].substring(1); // Убираем @
                String msg = parts[1];
                this.minecraft.getConnection().sendCommand("msg " + target + " " + msg);

                // Фейк сообщение себе в чат
                chatManager.addMessage(com.conquest.chat.enums.ChatMessageType.WHISPER,
                        Component.literal("Вы -> " + target + ": " + msg).withStyle(net.minecraft.ChatFormatting.GRAY));
            } else {
                this.minecraft.getConnection().sendChat(text);
            }
        } else if (currentTab.equals("Урон")) {
            // Block
        } else {
            this.minecraft.getConnection().sendChat(text);
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
