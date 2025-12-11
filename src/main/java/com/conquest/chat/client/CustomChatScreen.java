package com.conquest.chat.client;

import com.conquest.chat.config.ChatConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
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
        super.init();
        int chatWidth = ChatConfig.CLIENT.chatWidth.get();
        int chatHeight = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - chatHeight - 4;

        this.input.setX(x + 4);
        this.input.setY(y + chatHeight - 14);
        this.input.setWidth(chatWidth - 8);
        this.input.setBordered(false);
        this.input.setMaxLength(256);
        this.input.setTextColor(0xFFFFFFFF);

        // Включаем подсказки
        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 0, 10, false, -805306368);
        this.commandSuggestions.setAllowSuggestions(true);
        this.commandSuggestions.updateCommandInfo();

        // Логика авто-вставки @
        if (currentTab.equals("Личное") && (this.input.getValue().isEmpty() || this.input.getValue().equals("/"))) {
            this.input.setValue("@");
            this.input.setCursorPosition(1);
            this.input.setHighlightPos(1);
            this.commandSuggestions.updateCommandInfo();
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
        int bgAlpha = 200; // Чуть темнее фон (200/255)
        int finalBgColor = ((int)(bgAlpha * animationAlpha) << 24) | (bgColor & 0x00FFFFFF);

        int width = ChatConfig.CLIENT.chatWidth.get();
        int height = ChatConfig.CLIENT.chatHeight.get();
        int x = 4;
        int y = this.height - height - 4;

        graphics.fill(x, y, x + width, y + height, finalBgColor);
        graphics.fill(x, y, x + width, y + TAB_HEIGHT, 0xAA000000); // Хедер еще темнее

        renderTabs(graphics, mouseX, mouseY, x, y, width);
        renderMessages(graphics, mouseX, mouseY, x, y + TAB_HEIGHT + 4, width, height - TAB_HEIGHT - 18);
        renderSettingsButton(graphics, mouseX, mouseY, x + 4, y + 4);

        int inputLineY = y + height - 16;
        graphics.fill(x, inputLineY, x + width, inputLineY + 1, 0x44FFFFFF);

        RenderSystem.setShaderColor(1f, 1f, 1f, animationAlpha);
        this.input.render(graphics, mouseX, mouseY, partialTick);
        this.commandSuggestions.render(graphics, mouseX, mouseY);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        String[] tabs = {"Общий", "Торг", "Личное", "Урон", "+"};
        int tabX = x + 24;
        int tabPadding = 8; // Чуть больше отступ

        for (String tab : tabs) {
            int textWidth = this.font.width(tab);
            boolean isActive = tab.equals(currentTab);

            // Цвет текста: Активный = Белый (FFFFFFFF), Неактивный = Серый (FFAAAAAA)
            int color = isActive ? 0xFFFFFFFF : 0xFFAAAAAA;

            graphics.drawString(this.font, Component.literal(tab), tabX, y + 4, color, false);

            // Подчеркивание для активной вкладки
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
        int maxTextWidth = width - 12;
        for (Component msg : rawMessages) {
            wrappedLines.addAll(this.font.split(msg, maxTextWidth));
        }

        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = height / lineHeight;
        int totalLines = wrappedLines.size();

        // ФИКС СКРОЛЛА: Не даем уйти в минус
        if (scrollOffset < 0) scrollOffset = 0;
        int maxScroll = Math.max(0, totalLines - visibleLines);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

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

            if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
                String[] tabs = {"Общий", "Торг", "Личное", "Урон"};
                int tabX = x + 24;
                int tabPadding = 8;
                for (String tab : tabs) {
                    int textWidth = this.font.width(tab);
                    if (mouseX >= tabX && mouseX < tabX + textWidth + tabPadding * 2) {
                        if (!currentTab.equals(tab)) {
                            this.currentTab = tab;
                            this.scrollOffset = 0;
                            this.input.setValue("");
                            if (tab.equals("Личное")) {
                                this.input.setValue("@");
                            }
                        }
                        return true;
                    }
                    tabX += textWidth + tabPadding * 2;
                }
            }
        }

        if (this.input.mouseClicked(mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // TAB FIX: Если подсказки обработали нажатие, возвращаем true
        if (this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = this.input.getValue().trim();
            if (!text.isEmpty()) {
                sendMessageByTab(text);
                this.minecraft.gui.getChat().addRecentChat(text);
                this.input.setValue(currentTab.equals("Личное") ? "@" : "");
                if (!currentTab.equals("Личное")) {
                    this.minecraft.setScreen(null);
                }
            } else {
                this.minecraft.setScreen(null);
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendMessageByTab(String text) {
        if (currentTab.equals("Торг")) {
            this.minecraft.getConnection().sendChat("[Торг] " + text);
        } else if (currentTab.equals("Личное")) {
            if (text.startsWith("@") && text.contains(" ")) {
                String[] parts = text.split(" ", 2);
                String target = parts[0].substring(1);
                String msg = parts[1];
                this.minecraft.getConnection().sendCommand("msg " + target + " " + msg);
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
            // ФИКС СКРОЛЛА: Инверсия и границы
            // delta > 0 (колесико вверх) -> видим более старые сообщения (offset увеличивается)
            this.scrollOffset += (delta > 0) ? 1 : -1;

            // Границы проверяются в render(), но лучше и здесь
            if (this.scrollOffset < 0) this.scrollOffset = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
