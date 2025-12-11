package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.network.ChannelSyncPacket;
import com.conquest.chat.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CustomChatScreen extends ChatScreen {

    // --- КОНСТАНТЫ ДИЗАЙНА ---
    private static final int CHAT_WIDTH = 320;
    private static final int CHAT_HEIGHT = 180;
    private static final int TAB_HEIGHT = 16;
    private static final int INPUT_HEIGHT = 14;
    private static final int MARGIN_LEFT = 4;
    private static final int MARGIN_BOTTOM = 40;

    private int scrollOffset = 0;
    private long openTime; // Для анимации

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
        super.init();
        this.openTime = System.currentTimeMillis(); // Засекаем время открытия

        int inputY = this.height - MARGIN_BOTTOM + 4;
        this.input.setX(MARGIN_LEFT + 2);
        this.input.setY(inputY);
        this.input.setWidth(CHAT_WIDTH - 4);
        this.input.setBordered(false);

        tabs.clear();
        int chatTop = this.height - MARGIN_BOTTOM - CHAT_HEIGHT;
        int tabY = chatTop - TAB_HEIGHT;

        int startX = MARGIN_LEFT + 16;
        int tabWidth = 60;

        tabs.add(createTab(ChatChannel.ALL, "Общий", startX, tabY, tabWidth, TAB_HEIGHT));
        startX += tabWidth + 2;
        tabs.add(createTab(ChatChannel.TRADE, "Торг", startX, tabY, tabWidth, TAB_HEIGHT));
        startX += tabWidth + 2;
        tabs.add(createTab(ChatChannel.WHISPER, "Личное", startX, tabY, tabWidth, TAB_HEIGHT));
        startX += tabWidth + 2;
        try {
            tabs.add(createTab(ChatChannel.valueOf("COMBAT"), "Урон", startX, tabY, tabWidth, TAB_HEIGHT));
        } catch (IllegalArgumentException ignored) {}

        // Кнопка настроек
        int settingsBtnSize = 12;
        int settingsBtnX = MARGIN_LEFT + 2;
        int settingsBtnY = tabY + (TAB_HEIGHT - settingsBtnSize) / 2;

        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("⚙"), (btn) -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.sendSystemMessage(Component.literal("Настройки чата пока не реализованы"));
            }
        }).pos(settingsBtnX, settingsBtnY).size(settingsBtnSize, settingsBtnSize).build());
    }

    private Tab createTab(ChatChannel channel, String title, int x, int y, int w, int h) {
        Tab tab = new Tab(channel, title);
        tab.x = x; tab.y = y; tab.w = w; tab.h = h;
        return tab;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // --- АНИМАЦИЯ FADE-IN ---
        long elapsed = System.currentTimeMillis() - openTime;
        float alpha = Mth.clamp(elapsed / 200f, 0f, 1f); // 200ms на появление
        int bgAlpha = (int)(170 * alpha); // 0xAA = 170
        int textAlpha = (int)(255 * alpha);

        // Используем стек матриц для прозрачности (если нужно), но проще управлять цветом заливки
        // Увы, guiGraphics.fill поддерживает alpha, но drawString берет цвет как int.
        // Для простоты анимируем только фон.

        ChatChannel active = ClientChatManager.getInstance().getActiveTab();
        var messages = ClientChatManager.getInstance().getMessagesForTab(active);

        int chatBottom = this.height - MARGIN_BOTTOM;
        int chatTop = chatBottom - CHAT_HEIGHT;
        int chatLeft = MARGIN_LEFT;
        int chatRight = chatLeft + CHAT_WIDTH;

        // Фон чата с анимацией прозрачности
        int bgColor = (bgAlpha << 24) | 0x000000;
        guiGraphics.fill(chatLeft, chatTop, chatRight, chatBottom, bgColor);

        // Вкладки
        for (Tab tab : tabs) {
            boolean isActive = (tab.channel == active);
            boolean isHovered = tab.isInside(mouseX, mouseY);

            int tabAlpha = isActive ? (int)(255 * alpha) : (int)(170 * alpha);
            int color = isActive ? 0x202020 : 0x000000;
            if (isHovered && !isActive) color = 0x303030;

            int finalTabColor = (tabAlpha << 24) | color;
            guiGraphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + tab.h, finalTabColor);

            if (isActive) {
                guiGraphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + 1, (textAlpha << 24) | 0xFFFFFF);
            }

            int textColor = isActive ? 0xFFFFFF : 0xAAAAAA;
            // Применяем alpha к тексту
            int finalTextColor = (textAlpha << 24) | (textColor & 0x00FFFFFF);
            guiGraphics.drawCenteredString(this.font, tab.title, tab.x + tab.w / 2, tab.y + 4, finalTextColor);
        }

        // --- ОТРИСОВКА СООБЩЕНИЙ ---
        int lineHeight = this.font.lineHeight + 1;
        int maxLines = CHAT_HEIGHT / lineHeight;
        int totalMessages = messages.size();

        if (totalMessages <= maxLines) scrollOffset = 0;
        else scrollOffset = Mth.clamp(scrollOffset, 0, totalMessages - maxLines);

        int startIndex = totalMessages - 1 - scrollOffset;

        // Переменная для хранения стиля под мышкой (для тултипа)
        Style hoveredStyle = null;

        for (int i = 0; i < maxLines; i++) {
            int msgIndex = startIndex - i;
            if (msgIndex < 0) break;

            Component line = messages.get(msgIndex);
            int y = chatBottom - lineHeight * (i + 1);
            int x = chatLeft + 4;

            // Рисуем текст
            // ВАЖНО: 0xFFFFFF | (textAlpha << 24)
            guiGraphics.drawString(this.font, line, x, y + 2, (textAlpha << 24) | 0xFFFFFF);

            // Проверка на наведение (Hover)
            // Если мышка внутри строки по Y и внутри чата по X
            if (mouseY >= y + 2 && mouseY < y + 2 + this.font.lineHeight && mouseX >= x && mouseX < chatRight) {
                // Вычисляем смещение X относительно начала строки
                int xOffset = mouseX - x;
                // Получаем стиль компонента под курсором
                hoveredStyle = this.font.getSplitter().componentStyleAtWidth(line, xOffset);
            }
        }

        // --- SCROLLBAR ---
        if (totalMessages > maxLines) {
            int barHeight = Math.max(10, (int) ((float) maxLines / totalMessages * CHAT_HEIGHT));
            int trackHeight = CHAT_HEIGHT;
            int maxScroll = totalMessages - maxLines;
            float scrollPercent = (float) scrollOffset / maxScroll;
            int barY = chatBottom - barHeight - (int)(scrollPercent * (trackHeight - barHeight));
            int barX = chatRight - 4;

            guiGraphics.fill(barX, chatTop, barX + 2, chatBottom, (0x44 << 24) | 0xFFFFFF);
            guiGraphics.fill(barX, barY, barX + 2, barY + barHeight, (textAlpha << 24) | 0xFFFFFF);
        }

        // --- ПОЛЕ ВВОДА ---
        guiGraphics.fill(chatLeft, chatBottom, chatRight, chatBottom + INPUT_HEIGHT + 4, (0xCC << 24) | 0x000000);
        this.input.render(guiGraphics, mouseX, mouseY, partialTick);

        for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // --- ОТРИСОВКА ТУЛТИПА (ПОСЛЕ ВСЕГО) ---
        if (hoveredStyle != null && hoveredStyle.getHoverEvent() != null) {
            guiGraphics.renderComponentHoverEffect(this.font, hoveredStyle, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Вкладки
            for (Tab tab : tabs) {
                if (tab.isInside(mouseX, mouseY)) {
                    setActiveChannel(tab.channel);
                    playClickSound();
                    return true;
                }
            }

            // Клик по тексту (ClickEvent)
            // Повторяем логику поиска стиля из render
            ChatChannel active = ClientChatManager.getInstance().getActiveTab();
            var messages = ClientChatManager.getInstance().getMessagesForTab(active);
            int lineHeight = this.font.lineHeight + 1;
            int maxLines = CHAT_HEIGHT / lineHeight;
            int startIndex = messages.size() - 1 - scrollOffset;

            int chatBottom = this.height - MARGIN_BOTTOM;
            int chatLeft = MARGIN_LEFT;
            int chatRight = chatLeft + CHAT_WIDTH;

            if (mouseX >= chatLeft && mouseX < chatRight && mouseY >= chatBottom - CHAT_HEIGHT && mouseY < chatBottom) {
                for (int i = 0; i < maxLines; i++) {
                    int msgIndex = startIndex - i;
                    if (msgIndex < 0) break;

                    int y = chatBottom - lineHeight * (i + 1);
                    if (mouseY >= y + 2 && mouseY < y + 2 + this.font.lineHeight) {
                        Component line = messages.get(msgIndex);
                        int xOffset = (int)mouseX - (chatLeft + 4);
                        Style style = this.font.getSplitter().componentStyleAtWidth(line, xOffset);
                        if (style != null && this.handleComponentClicked(style)) {
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private void setActiveChannel(ChatChannel channel) {
        ClientChatManager.getInstance().setActiveTab(channel);
        scrollOffset = 0;
        try {
            NetworkHandler.CHANNEL.send(PacketDistributor.SERVER.noArg(), new ChannelSyncPacket(channel));
        } catch (Exception e) {
            ConquestChatMod.LOGGER.error("Failed to send channel sync packet", e);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0) scrollOffset++;
        else scrollOffset--;
        return true;
    }
}
