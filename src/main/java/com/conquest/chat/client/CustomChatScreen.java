package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.network.ChannelSyncPacket;
import com.conquest.chat.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom implementation of the Chat Screen ensuring STALCRAFT-like design.
 * Features:
 * - Tabbed channels (Global, Trade, etc.)
 * - Compact design at bottom-left
 * - Smooth fade-in animation
 * - Full support for Hover and Click events (links, items)
 */
public class CustomChatScreen extends ChatScreen {

    // --- DESIGN CONSTANTS ---
    private static final int CHAT_WIDTH = 320;
    private static final int CHAT_HEIGHT = 180;
    private static final int TAB_HEIGHT = 16;
    private static final int INPUT_HEIGHT = 14;
    private static final int MARGIN_LEFT = 4;
    private static final int MARGIN_BOTTOM = 40;

    // Animation constants
    private static final float ANIMATION_DURATION = 100f; // ms (was 200f)

    private int scrollOffset = 0;
    private long openTime;

    // Tabs logic
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
        this.openTime = System.currentTimeMillis();

        // Position the input field (EditBox)
        int inputY = this.height - MARGIN_BOTTOM + 4;
        this.input.setX(MARGIN_LEFT + 2);
        this.input.setY(inputY);
        this.input.setWidth(CHAT_WIDTH - 4);
        this.input.setBordered(false); // Custom background rendering

        // Initialize Tabs
        tabs.clear();
        int chatTop = this.height - MARGIN_BOTTOM - CHAT_HEIGHT;
        int tabY = chatTop - TAB_HEIGHT;

        int startX = MARGIN_LEFT + 16; // Offset for settings button
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

        // Settings Button (Stub)
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
        // --- ANIMATION ---
        long elapsed = System.currentTimeMillis() - openTime;
        float alpha = Mth.clamp(elapsed / ANIMATION_DURATION, 0f, 1f);

        // Alpha logic: 0xAA (170) is max opacity for bg, 0xFF (255) for text
        int bgAlpha = (int)(170 * alpha);
        int textAlpha = (int)(255 * alpha);

        ChatChannel active = ClientChatManager.getInstance().getActiveTab();
        var messages = ClientChatManager.getInstance().getMessagesForTab(active);

        int chatBottom = this.height - MARGIN_BOTTOM;
        int chatTop = chatBottom - CHAT_HEIGHT;
        int chatLeft = MARGIN_LEFT;
        int chatRight = chatLeft + CHAT_WIDTH;

        // Render Background
        int bgColor = (bgAlpha << 24) | 0x000000;
        guiGraphics.fill(chatLeft, chatTop, chatRight, chatBottom, bgColor);

        // Render Tabs
        for (Tab tab : tabs) {
            boolean isActive = (tab.channel == active);
            boolean isHovered = tab.isInside(mouseX, mouseY);

            int tabAlpha = isActive ? (int)(255 * alpha) : (int)(170 * alpha);
            int color = isActive ? 0x202020 : 0x000000;
            if (isHovered && !isActive) color = 0x303030;

            int finalTabColor = (tabAlpha << 24) | color;
            guiGraphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + tab.h, finalTabColor);

            // Active tab indicator (white line on top)
            if (isActive) {
                guiGraphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + 1, (textAlpha << 24) | 0xFFFFFF);
            }

            int textColor = isActive ? 0xFFFFFF : 0xAAAAAA;
            int finalTextColor = (textAlpha << 24) | (textColor & 0x00FFFFFF);
            guiGraphics.drawCenteredString(this.font, tab.title, tab.x + tab.w / 2, tab.y + 4, finalTextColor);
        }

        // --- MESSAGES RENDERING ---
        int lineHeight = this.font.lineHeight + 1;
        int maxLines = CHAT_HEIGHT / lineHeight;
        int totalMessages = messages.size();

        // Scroll calculation
        if (totalMessages <= maxLines) scrollOffset = 0;
        else scrollOffset = Mth.clamp(scrollOffset, 0, totalMessages - maxLines);

        int startIndex = totalMessages - 1 - scrollOffset;

        Style hoveredStyle = null;

        for (int i = 0; i < maxLines; i++) {
            int msgIndex = startIndex - i;
            if (msgIndex < 0) break;

            Component line = messages.get(msgIndex);
            int y = chatBottom - lineHeight * (i + 1);
            int x = chatLeft + 4;

            // Draw text with alpha
            guiGraphics.drawString(this.font, line, x, y + 2, (textAlpha << 24) | 0xFFFFFF);

            // Hover Check logic
            if (mouseY >= y + 2 && mouseY < y + 2 + this.font.lineHeight && mouseX >= x && mouseX < chatRight) {
                int xOffset = mouseX - x;
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

        // --- INPUT BACKGROUND ---
        guiGraphics.fill(chatLeft, chatBottom, chatRight, chatBottom + INPUT_HEIGHT + 4, (0xCC << 24) | 0x000000);

        // Render Widgets (Input, Buttons)
        this.input.render(guiGraphics, mouseX, mouseY, partialTick);
        for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        // --- HOVER TOOLTIP ---
        if (hoveredStyle != null && hoveredStyle.getHoverEvent() != null) {
            guiGraphics.renderComponentHoverEffect(this.font, hoveredStyle, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 1. Check Tabs
            for (Tab tab : tabs) {
                if (tab.isInside(mouseX, mouseY)) {
                    setActiveChannel(tab.channel);
                    playClickSound();
                    return true;
                }
            }

            // 2. Check Clickable Text (Links, Commands)
            if (isInsideChatArea(mouseX, mouseY)) {
                Style clickedStyle = getStyleAtPosition(mouseX, mouseY);
                if (clickedStyle != null && this.handleComponentClicked(clickedStyle)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // Helper to check if mouse is inside the message area
    private boolean isInsideChatArea(double mouseX, double mouseY) {
        int chatBottom = this.height - MARGIN_BOTTOM;
        int chatTop = chatBottom - CHAT_HEIGHT;
        int chatLeft = MARGIN_LEFT;
        int chatRight = chatLeft + CHAT_WIDTH;
        return mouseX >= chatLeft && mouseX < chatRight && mouseY >= chatTop && mouseY < chatBottom;
    }

    // Helper to get Style at mouse position (DRY principle)
    private Style getStyleAtPosition(double mouseX, double mouseY) {
        ChatChannel active = ClientChatManager.getInstance().getActiveTab();
        var messages = ClientChatManager.getInstance().getMessagesForTab(active);

        int lineHeight = this.font.lineHeight + 1;
        int maxLines = CHAT_HEIGHT / lineHeight;
        int startIndex = messages.size() - 1 - scrollOffset;
        int chatBottom = this.height - MARGIN_BOTTOM;
        int chatLeft = MARGIN_LEFT;

        for (int i = 0; i < maxLines; i++) {
            int msgIndex = startIndex - i;
            if (msgIndex < 0) break;

            int y = chatBottom - lineHeight * (i + 1);
            if (mouseY >= y + 2 && mouseY < y + 2 + this.font.lineHeight) {
                Component line = messages.get(msgIndex);
                int xOffset = (int)mouseX - (chatLeft + 4);
                return this.font.getSplitter().componentStyleAtWidth(line, xOffset);
            }
        }
        return null;
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
