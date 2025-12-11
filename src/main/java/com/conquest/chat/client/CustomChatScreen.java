package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.network.ChannelSyncPacket;
import com.conquest.chat.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class CustomChatScreen extends ChatScreen {

    // --- КОНСТАНТЫ ДИЗАЙНА ---
    private static final int CHAT_WIDTH = 320;   // Ширина чата
    private static final int CHAT_HEIGHT = 180;  // Высота области сообщений
    private static final int TAB_HEIGHT = 16;    // Высота вкладок
    private static final int INPUT_HEIGHT = 14;  // Высота поля ввода

    // Отступ от левого нижнего края экрана
    private static final int MARGIN_LEFT = 4;
    private static final int MARGIN_BOTTOM = 40; // Чуть выше хотбара

    private int scrollOffset = 0;

    // Вкладки
    private static class Tab {
        final ChatChannel channel;
        final String title;
        int x, y, w, h;

        Tab(ChatChannel channel, String title) {
            this.channel = channel;
            this.title = title;
        }

        boolean isInside(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }
    private final List<Tab> tabs = new ArrayList<>();

    public CustomChatScreen(String defaultText) {
        super(defaultText);
    }

    @Override
    protected void init() {
        // Инициализируем ванильное поле ввода и прочее
        super.init();

        // --- ПЕРЕОПРЕДЕЛЯЕМ ПОЗИЦИЮ INPUT ---
        // Получаем доступ к protected полю input (в ChatScreen оно называется input)
        // В 1.20+ это EditBox. Настраиваем его позицию под наш дизайн.
        int inputY = this.height - MARGIN_BOTTOM + 4;
        this.input.setX(MARGIN_LEFT + 2);
        this.input.setY(inputY);
        this.input.setWidth(CHAT_WIDTH - 4);
        // Убираем ванильный рендер фона поля ввода, чтобы нарисовать свой
        this.input.setBordered(false);

        // --- ИНИЦИАЛИЗАЦИЯ ВКЛАДОК И КНОПОК ---
        tabs.clear();
        int chatTop = this.height - MARGIN_BOTTOM - CHAT_HEIGHT;
        int tabY = chatTop - TAB_HEIGHT; // Вкладки НАД чатом

        // Сдвигаем вкладки вправо, чтобы не наезжали на кнопку настроек
        int startX = MARGIN_LEFT + 16;
        int tabWidth = 60;

        // Создаем вкладки
        tabs.add(createTab(ChatChannel.ALL, "Общий", startX, tabY, tabWidth, TAB_HEIGHT));
        startX += tabWidth + 2;
        tabs.add(createTab(ChatChannel.TRADE, "Торг", startX, tabY, tabWidth, TAB_HEIGHT));
        startX += tabWidth + 2;
        tabs.add(createTab(ChatChannel.WHISPER, "Личное", startX, tabY, tabWidth, TAB_HEIGHT));
        startX += tabWidth + 2;
        try {
            tabs.add(createTab(ChatChannel.valueOf("COMBAT"), "Урон", startX, tabY, tabWidth, TAB_HEIGHT));
        } catch (IllegalArgumentException ignored) {}

        // --- КНОПКА НАСТРОЕК (Шестеренка) ---
        int settingsBtnSize = 12;
        int settingsBtnX = MARGIN_LEFT + 2;
        // Центрируем кнопку по вертикали относительно вкладок
        int settingsBtnY = tabY + (TAB_HEIGHT - settingsBtnSize) / 2;

        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("⚙"), (btn) -> {
                    // Действие при клике: Выводим сообщение, так как GUI настроек пока нет
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal("Настройки чата пока не реализованы"));
                    }
                })
                .pos(settingsBtnX, settingsBtnY)
                .size(settingsBtnSize, settingsBtnSize)
                .build());
    }

    private Tab createTab(ChatChannel channel, String title, int x, int y, int w, int h) {
        Tab tab = new Tab(channel, title);
        tab.x = x; tab.y = y; tab.w = w; tab.h = h;
        return tab;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Мы НЕ вызываем super.render() и НЕ вызываем renderBackground(),
        // чтобы полностью контролировать отрисовку и не рисовать ванильный чат.

        ChatChannel active = ClientChatManager.getInstance().getActiveTab();
        var messages = ClientChatManager.getInstance().getMessagesForTab(active);

        // Координаты области чата
        int chatBottom = this.height - MARGIN_BOTTOM;
        int chatTop = chatBottom - CHAT_HEIGHT;
        int chatLeft = MARGIN_LEFT;
        int chatRight = chatLeft + CHAT_WIDTH;

        // --- ФОН ЧАТА И ВКЛАДОК ---
        // Рисуем фон под сообщениями (черный полупрозрачный)
        guiGraphics.fill(chatLeft, chatTop, chatRight, chatBottom, 0xAA000000);

        // Рисуем вкладки
        for (Tab tab : tabs) {
            boolean isActive = (tab.channel == active);
            boolean isHovered = tab.isInside(mouseX, mouseY);

            // Цвета вкладок
            int color = isActive ? 0xFF202020 : 0xAA000000; // Активная темнее/непрозрачнее, неактивная прозрачнее
            if (isHovered && !isActive) color = 0xAA303030; // Подсветка при наведении

            guiGraphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + tab.h, color);

            // Белая полоска сверху активной вкладки (как акцент)
            if (isActive) {
                guiGraphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + 1, 0xFFFFFFFF);
            }

            int textColor = isActive ? 0xFFFFFF : 0xAAAAAA;
            // Центрируем текст на вкладке
            guiGraphics.drawCenteredString(this.font, tab.title, tab.x + tab.w / 2, tab.y + 4, textColor);
        }

        // --- СООБЩЕНИЯ И СКРОЛЛИНГ ---
        int lineHeight = this.font.lineHeight + 1; // +1 пиксель межстрочный интервал
        int maxLines = CHAT_HEIGHT / lineHeight;   // Сколько строк влазит на экран
        int totalMessages = messages.size();

        // Корректировка скролла (чтобы не уйти за пределы)
        if (totalMessages <= maxLines) scrollOffset = 0;
        else scrollOffset = Mth.clamp(scrollOffset, 0, totalMessages - maxLines);

        // Рисуем сообщения (снизу вверх)
        // Начинаем с сообщения (последнее - скролл)
        int startIndex = totalMessages - 1 - scrollOffset;

        for (int i = 0; i < maxLines; i++) {
            int msgIndex = startIndex - i;
            if (msgIndex < 0) break; // Сообщения кончились

            Component line = messages.get(msgIndex);

            // Координата Y для строки (снизу вверх)
            int y = chatBottom - lineHeight * (i + 1);

            // Рисуем строку текста
            // +4 пикселя отступ слева
            guiGraphics.drawString(this.font, line, chatLeft + 4, y + 2, 0xFFFFFF);
        }

        // --- SCROLLBAR (Полоса прокрутки) ---
        if (totalMessages > maxLines) {
            int barHeight = (int) ((float) maxLines / totalMessages * CHAT_HEIGHT);
            barHeight = Math.max(10, barHeight); // Минимальная высота ползунка

            int trackHeight = CHAT_HEIGHT;
            int maxScroll = totalMessages - maxLines;
            float scrollPercent = (float) scrollOffset / maxScroll;

            // Позиция ползунка.
            // scrollOffset = 0 -> Мы внизу (видим новые) -> Ползунок внизу
            // scrollOffset = max -> Мы вверху (история) -> Ползунок вверху
            // Инвертируем логику Y, так как экранные координаты растут вниз
            int barY = chatBottom - barHeight - (int)(scrollPercent * (trackHeight - barHeight));

            int barX = chatRight - 4; // Справа внутри чата

            // Рисуем трек (фон полосы)
            guiGraphics.fill(barX, chatTop, barX + 2, chatBottom, 0x44FFFFFF);
            // Рисуем сам ползунок
            guiGraphics.fill(barX, barY, barX + 2, barY + barHeight, 0xFFFFFFFF);
        }

        // --- ПОЛЕ ВВОДА ---
        // Рисуем фон под полем ввода
        guiGraphics.fill(chatLeft, chatBottom, chatRight, chatBottom + INPUT_HEIGHT + 4, 0xCC000000);

        // Отрисовка текста и курсора в поле ввода (это делает сам EditBox)
        this.input.render(guiGraphics, mouseX, mouseY, partialTick);

        // Отрисовка виджетов (кнопка настроек и т.д.)
        // super.render не вызываем, поэтому виджеты надо рисовать вручную, если они не добавлены через addRenderableWidget
        // Но addRenderableWidget сам не рисует, он просто регистрирует. Рисовать надо циклом или через super.render
        // Так как мы не хотим рисовать ванильный фон, но хотим кнопки:
        for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // Подсказки команд (Command Suggestions)
        // Они важны для игры. В ванильном коде это вызов commandSuggestions.render(guiGraphics, mouseX, mouseY);
        // Но это приватное поле. Если commandSuggestions недоступно, придется обойтись без них или использовать Access Transformer.
        // Обычно в Forge можно получить доступ через reflection или если поле public/protected.
        // Если commandSuggestions нет, просто пропускаем.
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) {
            scrollOffset++; // Крутим вверх -> идем в историю
        } else if (delta < 0) {
            scrollOffset--; // Крутим вниз -> идем к новым
        }
        return true; // Событие обработано
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Обработка клика по вкладкам
        if (button == 0) { // ЛКМ
            for (Tab tab : tabs) {
                if (tab.isInside(mouseX, mouseY)) {
                    setActiveChannel(tab.channel);
                    // Проигрываем звук клика
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        }
        // Передаем клик дальше (в поле ввода, кнопки и т.д.)
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setActiveChannel(ChatChannel channel) {
        ClientChatManager.getInstance().setActiveTab(channel);
        scrollOffset = 0; // Сброс скролла при смене вкладки, чтобы видеть новые сообщения
        try {
            // Отправляем пакет на сервер о смене канала (для логики чата)
            NetworkHandler.CHANNEL.send(PacketDistributor.SERVER.noArg(), new ChannelSyncPacket(channel));
        } catch (Exception e) {
            ConquestChatMod.LOGGER.error("Failed to send channel sync packet", e);
        }
    }
}
