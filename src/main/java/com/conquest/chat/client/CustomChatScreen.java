package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.config.ChatConfig;
import com.conquest.chat.enums.ChatMessageType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
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

    private final List<ClickArea> clickAreas = new ArrayList<>();

    // --- КОНТЕКСТНОЕ МЕНЮ ---
    private ContextMenu currentContextMenu = null;

    public CustomChatScreen(String initialMessage) {
        super(initialMessage);
        this.openTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        this.currentTab = chatManager.getActiveTabName();

        // Исправляем дефолтную вкладку, если вдруг она была "Торг" в памяти
        if (this.currentTab == null || this.currentTab.equals("Торг")) {
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

        this.customCommandSuggestions = new CommandSuggestions(
                this.minecraft,
                this,
                this.input,
                this.font,
                false,
                false,
                0,
                7,
                false,
                -805306368
        );

        this.customCommandSuggestions.setAllowSuggestions(true);
        this.customCommandSuggestions.updateCommandInfo();

        this.input.setResponder((text) -> {
            if (this.customCommandSuggestions != null) {
                this.customCommandSuggestions.updateCommandInfo();
            }
        });
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
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
        int finalBgColor = ((int) (bgAlpha * animationAlpha) << 24) | (bgColor & 0x00FFFFFF);

        int width = ChatConfig.CLIENT.chatWidth.get();
        int height = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - height - 4;

        graphics.fill(x, y, x + width, y + height, finalBgColor);
        graphics.fill(x, y, x + width, y + TAB_HEIGHT, 0xAA000000);

        renderTabs(graphics, mouseX, mouseY, x, y, width);

        graphics.enableScissor(x, y + TAB_HEIGHT + 2, x + width, y + height - 16);
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

        // Hover Tooltip (только если меню закрыто)
        if (currentContextMenu == null) {
            Style hoveredStyle = getStyleAt(mouseX, mouseY);
            if (hoveredStyle != null && hoveredStyle.getHoverEvent() != null) {
                graphics.renderComponentHoverEffect(this.font, hoveredStyle, mouseX, mouseY);
            }
        }

        // Контекстное меню (поверх всего)
        if (currentContextMenu != null) {
            currentContextMenu.render(graphics, mouseX, mouseY, this.font);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        String[] tabs = {"Общий", "Торговый", "Личное", "Урон"};
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
            graphics.drawString(this.font, line, x + 4, currentY, 0xFFFFFFFF, false);

            clickAreas.add(new ClickArea(x + 4, currentY, this.font.width(line), lineHeight, line));
            currentY -= lineHeight;
        }

        if (totalLines > visibleLines) {
            int scrollBarX = x + width - SCROLLBAR_WIDTH - 2;
            int trackHeight = height;
            int scrollH = Math.max(10, (int) ((float) visibleLines / totalLines * trackHeight));
            float scrollP = (float) scrollOffset / (totalLines - visibleLines);
            int scrollY = y + trackHeight - scrollH - (int) (scrollP * (trackHeight - scrollH));

            graphics.fill(scrollBarX, y, scrollBarX + SCROLLBAR_WIDTH, y + trackHeight, 0x44000000);
            graphics.fill(scrollBarX, scrollY, scrollBarX + SCROLLBAR_WIDTH, scrollY + scrollH, 0xFFAAAAAA);
        }
    }

    private Style getStyleAt(double mouseX, double mouseY) {
        for (ClickArea area : clickAreas) {
            if (mouseY >= area.y && mouseY < area.y + area.height) {
                double relativeX = mouseX - area.x;
                if (relativeX >= 0 && relativeX <= area.width) {
                    return this.font.getSplitter().componentStyleAtWidth(area.line, (int) relativeX);
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
        // 1) Сначала проверяем контекстное меню
        if (currentContextMenu != null) {
            if (currentContextMenu.mouseClicked(mouseX, mouseY, button)) {
                return true;
            } else {
                currentContextMenu = null; // клик мимо меню -> закрыть
                return true; // не пропускать клик дальше
            }
        }

        // 2) Затем suggestions
        if (this.customCommandSuggestions != null && this.customCommandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int width = ChatConfig.CLIENT.chatWidth.get();
        int height = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - height - 4;

        // Скроллбар: захват перетаскивания
        int scrollBarX = x + width - SCROLLBAR_WIDTH - 2;
        if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + SCROLLBAR_WIDTH + 2
                && mouseY >= y + TAB_HEIGHT && mouseY <= y + height - 16) {
            this.isDraggingScrollbar = true;
            updateScroll(mouseY, y, height);
            return true;
        }

        // ЛКМ логика
        if (button == 0) {
            // Клик по "⚙" (пока заглушка)
            if (mouseX >= x + 4 && mouseX <= x + 12 && mouseY >= y + 3 && mouseY <= y + 11) {
                // можно открыть конфиг/экран настроек позже
                return true;
            }

            // Клик по вкладкам
            if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
                handleTabClick(mouseX, x, y);
                return true;
            }
        }

        // Клики по области сообщений (и ЛКМ/ПКМ по стилям)
        if (mouseY >= y + TAB_HEIGHT + 2 && mouseY <= y + height - 16) {
            Style style = getStyleAt(mouseX, mouseY);
            if (style != null && style.getClickEvent() != null) {
                if (button == 1) {
                    handleRightClick(mouseX, mouseY, style);
                    return true;
                } else if (button == 0) {
                    if (this.handleComponentClicked(style)) return true;
                }
            }
        }

        // Урон: только чтение
        if (currentTab.equals("Урон")) return true;

        // Клик по инпуту
        if (this.input.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isDraggingScrollbar) {
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

        for (Component msg : rawMessages) {
            wrappedLines.addAll(this.font.split(msg, maxTextWidth));
        }

        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = messageAreaHeight / lineHeight;
        int totalLines = wrappedLines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);

        if (maxScroll > 0) {
            double trackTop = y;
            double trackHeight = messageAreaHeight;
            double relativeY = mouseY - trackTop;
            relativeY = Mth.clamp(relativeY, 0, trackHeight);

            double scrollH = Math.max(10, (visibleLines / (float) totalLines) * trackHeight);
            double availableSpace = trackHeight - scrollH;

            double ratio = (trackHeight - relativeY - (scrollH / 2.0)) / availableSpace;
            ratio = Mth.clamp(ratio, 0, 1);

            this.scrollOffset = (int) (ratio * maxScroll);
        }
    }

    // --- ПКМ: ОТКРЫТИЕ МЕНЮ ---
    private void handleRightClick(double mouseX, double mouseY, Style style) {
        ClickEvent event = style.getClickEvent();
        if (event == null) return;

        if (event.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            String val = event.getValue();

            // Получаем чистый ник (без /w и @)
            String targetNick;
            if (val.startsWith("/w ")) {
                targetNick = val.substring(3).trim();
            } else if (val.startsWith("@")) {
                targetNick = val.substring(1).trim();
            } else {
                targetNick = val.trim();
            }

            if (!targetNick.isEmpty()) {
                this.currentContextMenu = new ContextMenu((int) mouseX, (int) mouseY, targetNick);
            }
        }
    }

    // --- ДЕЙСТВИЯ КНОПОК МЕНЮ ---
    private void openPrivateChat(String nick) {
        this.currentTab = "Личное";
        chatManager.setActiveTabName("Личное");
        this.scrollOffset = 0;

        this.input.setValue("/w " + nick + " ");
        this.input.setCursorPosition(this.input.getValue().length());
        this.input.setHighlightPos(this.input.getValue().length());
        this.input.setFocused(true);
        this.setFocused(this.input);

        if (this.customCommandSuggestions != null) this.customCommandSuggestions.updateCommandInfo();
        this.currentContextMenu = null;
    }

    private void ignorePlayer(String nick) {
        ClientChatManager.getInstance().toggleIgnorePlayer(nick);
        this.currentContextMenu = null;
    }

    private void openProfile(String nick) {
        // Заглушка
        chatManager.addMessage(ChatMessageType.SYSTEM, Component.literal("§e[!] Профиль игрока " + nick + " (в разработке)"));
        this.currentContextMenu = null;
    }

    private void sendCommand(String command) {
        if (this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand(command);
        }
        this.currentContextMenu = null;
    }

    private void handleTabClick(double mouseX, int x, int y) {
        String[] tabs = {"Общий", "Торговый", "Личное", "Урон"};
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
        // Закрытие меню по ESC
        if (currentContextMenu != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            currentContextMenu = null;
            return true;
        }

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
        if (this.minecraft.getConnection() == null) return;

        if (currentTab.equals("Торговый")) {
            if (text.startsWith("/")) {
                this.minecraft.getConnection().sendCommand(text.substring(1));
            } else {
                this.minecraft.getConnection().sendChat("[Торг] " + text);
            }

        } else if (currentTab.equals("Личное")) {
            if (text.startsWith("/")) {
                this.minecraft.getConnection().sendCommand(text.substring(1));
            } else {
                ClientChatManager.getInstance().addMessage(
                        ChatMessageType.GENERAL,
                        Component.literal("§c[!] Во вкладке 'Личное' можно писать только команды (напр. /msg Nick).")
                );
            }

        } else if (currentTab.equals("Урон")) {
            // Block (read-only)
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

    private static class ClickArea {
        final double x, y, width, height;
        final FormattedCharSequence line;

        ClickArea(double x, double y, double width, double height, FormattedCharSequence line) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.line = line;
        }
    }

    // ==========================================================
    // ВНУТРЕННИЙ КЛАСС КОНТЕКСТНОГО МЕНЮ (расширенный)
    // ==========================================================
    private class ContextMenu {

        private final int x;
        private final int width = 110;
        private final int buttonHeight = 14;

        private final String targetNick;
        private final List<ContextButton> buttons = new ArrayList<>();

        private final int renderY;

        public ContextMenu(int mouseX, int mouseY, String targetNick) {
            this.x = mouseX;
            this.targetNick = targetNick;

            // Пункты меню
            buttons.add(new ContextButton("Профиль", () -> openProfile(targetNick)));
            buttons.add(new ContextButton("Написать ЛС", () -> openPrivateChat(targetNick)));
            buttons.add(new ContextButton("В друзья", () -> sendCommand("friend add " + targetNick)));
            buttons.add(new ContextButton("В пати", () -> sendCommand("party invite " + targetNick)));

            String ignoreText = ClientChatManager.getInstance().isPlayerIgnored(targetNick)
                    ? "Разблокировать"
                    : "Игнорировать";
            buttons.add(new ContextButton(ignoreText, () -> ignorePlayer(targetNick)));

            // Геометрия: 14px — шапка (ник + разделитель)
            int totalHeight = buttons.size() * buttonHeight + 14;

            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            int yCandidate = mouseY;

            // Если не влезает вниз — рисуем вверх от курсора
            if (yCandidate + totalHeight > screenHeight) {
                yCandidate = mouseY - totalHeight;
            }

            // На всякий — не уходить выше 0
            this.renderY = Math.max(0, yCandidate);
        }

        public void render(GuiGraphics graphics, int mouseX, int mouseY, net.minecraft.client.gui.Font font) {
            int height = buttons.size() * buttonHeight + 14;

            graphics.fill(x, renderY, x + width, renderY + height, 0xF0000000);
            graphics.renderOutline(x, renderY, width, height, 0xFFFFFFFF);

            graphics.drawCenteredString(font, targetNick, x + width / 2, renderY + 3, 0xFFFFFF00);
            graphics.fill(x, renderY + 13, x + width, renderY + 14, 0xFF555555);

            int currentY = renderY + 14;
            for (ContextButton btn : buttons) {
                boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= currentY && mouseY < currentY + buttonHeight;
                int bgColor = hovered ? 0xFF444444 : 0x00000000;

                graphics.fill(x + 1, currentY, x + width - 1, currentY + buttonHeight, bgColor);
                graphics.drawString(font, btn.text, x + 5, currentY + 3, 0xFFFFFFFF, false);

                currentY += buttonHeight;
            }
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;

            int currentY = renderY + 14;
            for (ContextButton btn : buttons) {
                if (mouseX >= x && mouseX < x + width && mouseY >= currentY && mouseY < currentY + buttonHeight) {
                    btn.action.run();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                    );
                    return true;
                }
                currentY += buttonHeight;
            }

            // Если кликнули по меню, но не по кнопке — поглощаем клик, чтобы не прокликать чат/ссылки.
            int totalHeight = buttons.size() * buttonHeight + 14;
            return mouseX >= x && mouseX < x + width && mouseY >= renderY && mouseY < renderY + totalHeight;
        }

        private class ContextButton {
            final String text;
            final Runnable action;

            ContextButton(String text, Runnable action) {
                this.text = text;
                this.action = action;
            }
        }
    }
}
