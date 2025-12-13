package com.conquest.chat.client;

import com.conquest.chat.channel.ChannelType;
import com.conquest.chat.client.ui.ChatNineSlice;
import com.conquest.chat.client.ui.ChatTextures;
import com.conquest.chat.config.ChatConfig;
import com.conquest.chat.enums.ChatMessageType;
import com.conquest.chat.network.ChatMessagePacket;
import com.conquest.chat.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
public class CustomChatScreen extends ChatScreen {

    private static final int TAB_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 4;

    // Контекстное меню: разрешаем только ники
    private static final Pattern NICK_ONLY = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

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

    // --- ITEM PICKER ---
    private final ItemPickerOverlay itemPicker = new ItemPickerOverlay();

    // ---------------------------
    // Двухслойный ввод предметов
    // ---------------------------
    private static final class PendingItem {
        int start;
        int end;
        final int slotMain;
        final String placeholder;
        final String id;

        PendingItem(int start, int end, int slotMain, String placeholder, String id) {
            this.start = start;
            this.end = end;
            this.slotMain = slotMain;
            this.placeholder = placeholder;
            this.id = id;
        }
    }

    private final List<PendingItem> pendingItems = new ArrayList<>();
    private String lastInputValue = "";

    // TAB hold state (чтобы TAB работал по удержанию, а не как toggle)
    private boolean tabHeld = false;

    // ==========================
    // FPS/GC: layout cache
    // ==========================
    private final List<FormattedCharSequence> cachedWrappedLines = new ArrayList<>();
    private List<Component> cachedRawMessagesRef = null;
    private Component cachedFirstMsg = null;
    private Component cachedLastMsg = null;
    private String cachedTab = null;
    private int cachedMaxTextWidth = -1;
    private int cachedMsgCount = -1;

    // ======= WRAP FIX (class-level) =======

    private List<FormattedCharSequence> wrapMessage(Component component, int maxWidthPx) {
        Font font = this.font;
        List<FormattedCharSequence> base = font.split(component, maxWidthPx); // split by width [web:451]

        ArrayList<FormattedCharSequence> fixed = new ArrayList<>();
        for (FormattedCharSequence seq : base) {
            if (font.width(seq) <= maxWidthPx) { // [web:451]
                fixed.add(seq);
            } else {
                fixed.addAll(breakLongWord(seq, maxWidthPx));
            }
        }
        return fixed;
    }

    private List<FormattedCharSequence> breakLongWord(FormattedCharSequence seq, int maxWidthPx) {
        Font font = this.font;
        String raw = seq.toString();

        ArrayList<FormattedCharSequence> out = new ArrayList<>();
        int i = 0;

        while (i < raw.length()) {
            int j = i + 1;
            int lastFit = j;

            while (j <= raw.length()) {
                String sub = raw.substring(i, j);
                if (font.width(sub) <= maxWidthPx) { // [web:451]
                    lastFit = j;
                    j++;
                } else {
                    break;
                }
            }

            if (lastFit <= i) lastFit = Math.min(i + 1, raw.length());
            String chunk = raw.substring(i, lastFit);

            out.add(FormattedCharSequence.forward(chunk, Style.EMPTY));
            i = lastFit;
        }

        return out;
    }

// ======= END WRAP FIX =======


    public CustomChatScreen(String initialMessage) {
        super(initialMessage);
        this.openTime = System.currentTimeMillis();
    }

    // ---------------------------
    // Layout helpers
    // ---------------------------
    private int chatX() {
        return 4;
    }

    private int chatW() {
        return ChatConfig.CLIENT.chatWidth.get();
    }

    private int chatH() {
        return ChatConfig.CLIENT.chatHeight.get();
    }

    private int chatY() {
        return this.height - chatH() - 4;
    }

    private int messageTopY(int y) {
        return y + TAB_HEIGHT + 2;
    }

    private int messageBottomY(int y, int height) {
        return y + height - 16; // область до инпута
    }

    private boolean isInsideChatPanel(double mouseX, double mouseY) {
        int x = chatX();
        int y = chatY();
        int w = chatW();
        int h = chatH();
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean isInsideMessagesArea(double mouseX, double mouseY) {
        int x = chatX();
        int y = chatY();
        int w = chatW();
        int h = chatH();
        return mouseX >= x
                && mouseX < x + w
                && mouseY >= messageTopY(y)
                && mouseY <= messageBottomY(y, h);
    }

    private void applyChatLayout() {
        int chatWidth = chatW();
        int chatHeight = chatH();
        int x = chatX();
        int y = chatY();

        // ---- input field geometry (рисуем фон отдельно, EditBox — внутри с padding) ----
        int inputFieldX = x + 4;
        int inputFieldY = y + chatHeight - 16 + 1;
        int inputFieldW = chatWidth - 8;

        // сам EditBox (текст) — с внутренними отступами, чтобы совпасть с рефом
        this.input.setX(inputFieldX + 4);
        this.input.setY(inputFieldY + 3);
        this.input.setWidth(inputFieldW - 8);

        this.input.setBordered(false);
        this.input.setMaxLength(256);
        this.input.setTextColor(0xFFFFFFFF);

        if (this.minecraft == null || this.font == null) {
            this.customCommandSuggestions = null;
            return;
        }

        // ВАЖНО: sx/sy — точка привязки для подсказок.
        // Они всё равно будут зажаты scissor'ом внутри панели чата.
        int sx = this.input.getX();
        int sy = this.input.getY() - (this.font.lineHeight + 2);

        this.customCommandSuggestions = new CommandSuggestions(
                this.minecraft, this, this.input, this.font,
                false, false,
                sx, sy,
                false,
                0xD0000000
        );
        this.customCommandSuggestions.setAllowSuggestions(true);
        this.customCommandSuggestions.updateCommandInfo();
    }

    @Override
    protected void init() {
        this.currentTab = chatManager.getActiveTabName();
        if (this.currentTab == null || this.currentTab.isEmpty()) {
            this.currentTab = "Общий";
            chatManager.setActiveTabName("Общий");
        }

        super.init();
        applyChatLayout();

        this.itemPicker.setOnInsert(this::onItemInserted);

        this.input.setResponder((text) -> {
            onInputChanged(text);
            if (this.customCommandSuggestions != null) {
                this.customCommandSuggestions.updateCommandInfo();
            }
        });

        lastInputValue = this.input.getValue();
        invalidateWrappedCache();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        applyChatLayout();
        invalidateWrappedCache();
    }

    // ---------------------------
    // Rendering helpers (textures)
    // ---------------------------
    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.setColor(1f, 1f, 1f, animationAlpha);
        ChatNineSlice.blit9(g, ChatTextures.PANEL_9, x, y, w, h, 2, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private void drawTab(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered) {
        // базовая таб-кнопка
        float a = animationAlpha * (active ? 1.0f : (hovered ? 0.90f : 0.75f));
        g.setColor(1f, 1f, 1f, a);

        var tex = active ? ChatTextures.TAB_ACTIVE_9 : ChatTextures.TAB_INACTIVE_9;
        // border=2 чтобы рамка была тонкой как в рефе
        ChatNineSlice.blit9(g, tex, x, y, w, h, 2, 32, 16);

        g.setColor(1f, 1f, 1f, 1f);

        // нижняя полоска активной вкладки
        if (active) {
            int y0 = y + h - 2;
            g.hLine(x + 3, x + w - 4, y0, 0xCCFFFFFF);
        }
    }

    private void drawScrollTrack(GuiGraphics g, int x, int y, int w, int h) {
        g.setColor(1f, 1f, 1f, animationAlpha);
        // используем nine-slice как безопасное растягивание маленькой текстуры
        ChatNineSlice.blit9(g, ChatTextures.SCROLL_TRACK, x, y, w, h, 1, 4, 32);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private void drawScrollThumb(GuiGraphics g, int x, int y, int w, int h) {
        g.setColor(1f, 1f, 1f, animationAlpha);
        ChatNineSlice.blit9(g, ChatTextures.SCROLL_THUMB, x, y, w, h, 1, 4, 16);
        g.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - openTime;
        float duration = ChatConfig.CLIENT.fadeDuration.get();
        this.animationAlpha = duration > 0 ? Mth.clamp(elapsed / duration, 0.0f, 1.0f) : 1.0f;

        int width = chatW();
        int height = chatH();
        int x = chatX();
        int y = chatY();

        // Background panel
        drawPanel(graphics, x, y, width, height);

        drawCorners(graphics, x, y, width, height, animationAlpha);
        // Tabs
        renderTabs(graphics, mouseX, mouseY, x, y, width);

        // Messages area (clipped)
        graphics.enableScissor(x, messageTopY(y), x + width, messageBottomY(y, height));
        this.clickAreas.clear();
        renderMessages(graphics, mouseX, mouseY, x, messageTopY(y), width, height - TAB_HEIGHT - 16);
        graphics.disableScissor();

        renderSettingsButton(graphics, mouseX, mouseY, x, y);

        // Input + suggestions (кроме "Урон")
        if (!"Урон".equals(currentTab)) {
            int inputLineY = y + height - 16;

            // верхний разделитель над инпутом
            graphics.hLine(x + 3, x + width - 4, inputLineY, 0x55FFFFFF);

            // фон поля ввода (рамка + подложка)
            int inputFieldX = x + 4;
            int inputFieldY = inputLineY + 1;
            int inputFieldW = width - 8;
            int inputFieldH = 14;

            graphics.setColor(1f, 1f, 1f, animationAlpha);
            ChatNineSlice.blit9(graphics, ChatTextures.INPUT_FIELD_9,
                    inputFieldX, inputFieldY, inputFieldW, inputFieldH,
                    2, 32, 16);
            graphics.setColor(1f, 1f, 1f, 1f);

            // EditBox (текст) + подсказки
            RenderSystem.setShaderColor(1f, 1f, 1f, animationAlpha);
            this.input.render(graphics, mouseX, mouseY, partialTick);

            if (this.customCommandSuggestions != null) {
                graphics.enableScissor(x, y, x + width, y + height);
                this.customCommandSuggestions.render(graphics, mouseX, mouseY);
                graphics.disableScissor();
            }
        } else {
            graphics.drawString(this.font, "Только чтение", x + 4, y + height - 12, 0xFFAAAAAA, false);
        }

        // Overlay всегда поверх
        if (itemPicker.isOpen()) {
            int chatWidth = chatW();
            int chatHeight = chatH();
            int chatX = chatX();
            int chatY = chatY();
            itemPicker.layout(chatX, chatY, chatWidth, chatHeight, this.width, this.height);
            itemPicker.render(graphics, mouseX, mouseY, partialTick);
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
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawCorners(GuiGraphics g, int x, int y, int w, int h, float alpha) {
        int s = 12;     // размер corner_12.png
        int pad = 2;    // отступ от краёв панели, подгоняется под глаз

        g.setColor(1f, 1f, 1f, alpha);

        // TL
        g.blit(ChatTextures.CORNER_12, x + pad, y + pad, 0, 0, s, s, s, s);

        // TR (отзеркалить по X)
        g.pose().pushPose();
        g.pose().translate(x + w - pad, y + pad, 0);
        g.pose().scale(-1f, 1f, 1f);
        g.blit(ChatTextures.CORNER_12, 0, 0, 0, 0, s, s, s, s);
        g.pose().popPose();

        // BL (отзеркалить по Y)
        g.pose().pushPose();
        g.pose().translate(x + pad, y + h - pad, 0);
        g.pose().scale(1f, -1f, 1f);
        g.blit(ChatTextures.CORNER_12, 0, 0, 0, 0, s, s, s, s);
        g.pose().popPose();

        // BR (отзеркалить по X и Y)
        g.pose().pushPose();
        g.pose().translate(x + w - pad, y + h - pad, 0);
        g.pose().scale(-1f, -1f, 1f);
        g.blit(ChatTextures.CORNER_12, 0, 0, 0, 0, s, s, s, s);
        g.pose().popPose();

        g.setColor(1f, 1f, 1f, 1f);
    }

    private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width) {
        // общий фон/полоса для ряда вкладок как в рефе
        graphics.setColor(1f, 1f, 1f, animationAlpha);
        ChatNineSlice.blit9(graphics, ChatTextures.TAB_ROW_BG_9, x + 4, y + 1, width - 8, TAB_HEIGHT, 2, 32, 16);
        graphics.setColor(1f, 1f, 1f, 1f);

        String[] tabs = {"Общий", "Торговый", "Личное", "Урон"};

        int tabX = x + 8;
        int tabPadding = 8;

        // легкий разделитель под вкладками
        graphics.hLine(x + 3, x + width - 4, y + TAB_HEIGHT, 0x33FFFFFF);

        for (int i = 0; i < tabs.length; i++) {
            String tab = tabs[i];

            int textWidth = this.font.width(tab);
            int btnW = textWidth + tabPadding * 2;
            int btnH = TAB_HEIGHT;

            boolean isActive = tab.equals(currentTab);
            boolean hovered = mouseX >= tabX && mouseX < tabX + btnW && mouseY >= y && mouseY < y + btnH;

            drawTab(graphics, tabX, y, btnW, btnH, isActive, hovered);

            int textX = tabX + tabPadding;
            int textY = y + 4;
            int color = isActive ? 0xFFFFFFFF : 0xFFB0B0B0;
            graphics.drawString(this.font, Component.literal(tab), textX, textY, color, false);

            // вертикальный разделитель между вкладками (кроме последней)
            if (i < tabs.length - 1) {
                int dividerX = tabX + btnW + 2;
                graphics.setColor(1f, 1f, 1f, animationAlpha);
                graphics.blit(ChatTextures.TAB_DIVIDER, dividerX, y + 2, 0, 0, 1, TAB_HEIGHT - 4, 1, 16);
                graphics.setColor(1f, 1f, 1f, 1f);
            }

            tabX += btnW + 4;
            if (tabX > x + width - 20) break;
        }
    }

    private void renderMessages(GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int width, int height) {
        List<Component> rawMessages = chatManager.getMessages(currentTab);
        if (rawMessages == null || rawMessages.isEmpty()) return;

        int maxTextWidth = width - 12 - SCROLLBAR_WIDTH;

        // FPS/GC: кэшируем перенос строк
        List<FormattedCharSequence> wrappedLines = getWrappedLinesCached(rawMessages, maxTextWidth);

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

        // Скроллбар
        if (totalLines > visibleLines) {
            int scrollBarX = x + width - SCROLLBAR_WIDTH - 2;
            int trackHeight = height;

            int scrollH = Math.max(10, (int) ((float) visibleLines / totalLines * trackHeight));
            float scrollP = (float) scrollOffset / (totalLines - visibleLines);
            int scrollY = y + trackHeight - scrollH - (int) (scrollP * (trackHeight - scrollH));

            drawScrollTrack(graphics, scrollBarX, y, SCROLLBAR_WIDTH, trackHeight);
            drawScrollThumb(graphics, scrollBarX, scrollY, SCROLLBAR_WIDTH, scrollH);
        }
    }

    private void invalidateWrappedCache() {
        cachedWrappedLines.clear();
        cachedRawMessagesRef = null;
        cachedFirstMsg = null;
        cachedLastMsg = null;
        cachedTab = null;
        cachedMaxTextWidth = -1;
        cachedMsgCount = -1;
    }

    private List<FormattedCharSequence> getWrappedLinesCached(List<Component> rawMessages, int maxTextWidth) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            invalidateWrappedCache();
            return cachedWrappedLines;
        }

        Component first = rawMessages.get(0);
        Component last = rawMessages.get(rawMessages.size() - 1);

        boolean invalid =
                rawMessages != cachedRawMessagesRef ||
                        !Objects.equals(currentTab, cachedTab) ||
                        maxTextWidth != cachedMaxTextWidth ||
                        rawMessages.size() != cachedMsgCount ||
                        first != cachedFirstMsg ||
                        last != cachedLastMsg;

        if (invalid) {
            cachedWrappedLines.clear();
            for (Component msg : rawMessages) {
                cachedWrappedLines.addAll(wrapMessage(msg, maxTextWidth));
            }
            cachedRawMessagesRef = rawMessages;
            cachedTab = currentTab;
            cachedMaxTextWidth = maxTextWidth;
            cachedMsgCount = rawMessages.size();
            cachedFirstMsg = first;
            cachedLastMsg = last;
        }

        return cachedWrappedLines;
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
        graphics.drawString(this.font, Component.literal("⚙"), x + 4, y + 3, 0xFFB0B0B0, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1) Контекстное меню
        if (currentContextMenu != null) {
            if (currentContextMenu.mouseClicked(mouseX, mouseY, button)) return true;
            currentContextMenu = null;
            return true; // клик мимо меню -> закрыть и поглотить
        }

        // 2) Item picker
        if (itemPicker.isOpen() && itemPicker.mouseClicked(mouseX, mouseY, button, this.input)) {
            return true;
        }

        // 3) Suggestions (только если клик внутри панели чата)
        if (isInsideChatPanel(mouseX, mouseY) && this.customCommandSuggestions != null) {
            if (this.customCommandSuggestions.mouseClicked(mouseX, mouseY, button)) return true;
        }

        int width = chatW();
        int height = chatH();
        int x = chatX();
        int y = chatY();

        // Скроллбар: захват перетаскивания
        int scrollBarX = x + width - SCROLLBAR_WIDTH - 2;
        if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + SCROLLBAR_WIDTH + 2
                && mouseY >= messageTopY(y) && mouseY <= messageBottomY(y, height)) {
            this.isDraggingScrollbar = true;
            updateScroll(mouseY, y, height);
            return true;
        }

        // Клик по "⚙" (пока заглушка)
        if (button == 0) {
            if (mouseX >= x + 4 && mouseX <= x + 12 && mouseY >= y + 3 && mouseY <= y + 11) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }

        // Клик по вкладкам
        if (button == 0) {
            if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
                handleTabClick(mouseX, mouseY, x, y);
                return true;
            }
        }

        // Клики по области сообщений
        if (isInsideMessagesArea(mouseX, mouseY)) {
            Style style = getStyleAt(mouseX, mouseY);

            // ПКМ: только контекстное меню (и только по /w Nick)
            if (button == 1) {
                if (tryOpenContextMenu(mouseX, mouseY, style)) return true;
                return true; // поглощаем ПКМ в области сообщений
            }

            // ЛКМ: обычные клики по компонентам
            if (button == 0 && style != null && style.getClickEvent() != null) {
                if (this.handleComponentClicked(style)) return true;
            }
        }

        // "Урон": только чтение
        if ("Урон".equals(currentTab)) return true;

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
            int height = chatH();
            int y = chatY();
            updateScroll(mouseY, y, height);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void updateScroll(double mouseY, int y, int height) {
        int messageAreaHeight = height - TAB_HEIGHT - 16;

        List<Component> rawMessages = chatManager.getMessages(currentTab);
        if (rawMessages == null || rawMessages.isEmpty()) return;

        int width = chatW();
        int maxTextWidth = width - 12 - SCROLLBAR_WIDTH;

        // используем тот же кэш, что и отрисовка
        List<FormattedCharSequence> wrappedLines = getWrappedLinesCached(rawMessages, maxTextWidth);

        int lineHeight = this.font.lineHeight + 1;
        int visibleLines = messageAreaHeight / lineHeight;
        int totalLines = wrappedLines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);

        if (maxScroll <= 0) {
            this.scrollOffset = 0;
            return;
        }

        double trackTop = y + TAB_HEIGHT + 2;
        double trackHeight = messageAreaHeight;
        double relativeY = mouseY - trackTop;

        relativeY = Mth.clamp(relativeY, 0, trackHeight);

        double scrollH = Math.max(10, (visibleLines / (float) totalLines) * trackHeight);
        double availableSpace = trackHeight - scrollH;

        // центрируем thumb относительно мыши
        double ratio = (trackHeight - relativeY - (scrollH / 2.0)) / availableSpace;
        ratio = Mth.clamp(ratio, 0, 1);

        this.scrollOffset = (int) (ratio * maxScroll);
    }

    // ---------------------------
    // ПКМ: открытие меню
    // ---------------------------
    private boolean tryOpenContextMenu(double mouseX, double mouseY, Style style) {
        ClickEvent event = (style == null) ? null : style.getClickEvent();
        String nick = extractNickForContextMenu(event);
        if (nick == null) return false;

        this.currentContextMenu = new ContextMenu((int) mouseX, (int) mouseY, nick);
        return true;
    }

    /**
     * Разрешаем контекстное меню ТОЛЬКО если clickEvent — это SUGGEST_COMMAND формата "/w Nick ...".
     * Все остальные SUGGEST_COMMAND (например "/friend add ...") игнорируются.
     */
    private String extractNickForContextMenu(ClickEvent event) {
        if (event == null) return null;
        if (event.getAction() != ClickEvent.Action.SUGGEST_COMMAND) return null;

        String val = event.getValue();
        if (val == null) return null;

        val = val.trim();
        if (!val.startsWith("/w ")) return null;

        String rest = val.substring(3).trim();
        if (rest.isEmpty()) return null;

        String nick = rest.split("\\s+", 2)[0].trim();
        if (!NICK_ONLY.matcher(nick).matches()) return null;

        return nick;
    }

    private void openPrivateChat(String nick) {
        this.currentTab = "Личное";
        chatManager.setActiveTabName("Личное");
        this.scrollOffset = 0;

        this.input.setValue("/w " + nick + " ");
        this.input.setCursorPosition(this.input.getValue().length());
        this.input.setHighlightPos(this.input.getValue().length());

        this.input.setFocused(true);
        this.setFocused(this.input);

        if (this.customCommandSuggestions != null) {
            this.customCommandSuggestions.updateCommandInfo();
        }

        this.currentContextMenu = null;
        invalidateWrappedCache();
    }

    private void ignorePlayer(String nick) {
        ClientChatManager.getInstance().toggleIgnorePlayer(nick);
        this.currentContextMenu = null;
        invalidateWrappedCache();
    }

    private void openProfile(String nick) {
        chatManager.addMessage(
                ChatMessageType.SYSTEM,
                Component.literal("§e[!] Профиль игрока " + nick + " (в разработке)")
        );
        this.currentContextMenu = null;
        invalidateWrappedCache();
    }

    private void sendCommand(String command) {
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().sendCommand(command);
        }
        this.currentContextMenu = null;
    }

    private void clearPendingItems() {
        pendingItems.clear();
    }

    private static String newItemId() {
        // Короткий id для токена. Игрок его не видит, потому что плейсхолдеры заменяются только при отправке.
        long v = ThreadLocalRandom.current().nextLong();
        if (v == 0L) v = 1L;
        return Long.toUnsignedString(v, 36);
    }

    private void onItemInserted(ItemPickerOverlay.InsertedItem inserted) {
        if (inserted == null) return;
        pendingItems.add(new PendingItem(
                inserted.start(),
                inserted.end(),
                inserted.slotMain(),
                inserted.placeholder(),
                newItemId()
        ));
    }

    /**
     * Поддерживаем PendingItem диапазоны при любом редактировании инпута.
     * Если игрок меняет текст внутри плейсхолдера — считаем вложение удалённым.
     */
    private void onInputChanged(String newText) {
        if (newText == null) newText = "";
        String oldText = (lastInputValue == null) ? "" : lastInputValue;

        if (oldText.equals(newText)) return;

        // Найти общий префикс
        int prefix = 0;
        int min = Math.min(oldText.length(), newText.length());
        while (prefix < min && oldText.charAt(prefix) == newText.charAt(prefix)) {
            prefix++;
        }

        // Найти общий суффикс
        int oldSuffix = oldText.length();
        int newSuffix = newText.length();
        while (oldSuffix > prefix && newSuffix > prefix
                && oldText.charAt(oldSuffix - 1) == newText.charAt(newSuffix - 1)) {
            oldSuffix--;
            newSuffix--;
        }

        int oldMidLen = oldSuffix - prefix;
        int newMidLen = newSuffix - prefix;
        int delta = newMidLen - oldMidLen;

        final int prefixF = prefix;
        final int oldSuffixF = oldSuffix;
        final int deltaF = delta;

        if (!pendingItems.isEmpty()) {
            pendingItems.removeIf(p -> {
                // полностью до изменения
                if (p.end <= prefixF) {
                    return false;
                }
                // полностью после изменения
                if (p.start >= oldSuffixF) {
                    p.start += deltaF;
                    p.end += deltaF;
                    return false;
                }
                // пересечение => плейсхолдер испорчен/изменён
                return true;
            });
        }

        lastInputValue = newText;
    }

    private static final class PreparedOutgoing {
        final String text;
        final Map<String, Integer> itemIdToSlot;

        PreparedOutgoing(String text, Map<String, Integer> itemIdToSlot) {
            this.text = text;
            this.itemIdToSlot = itemIdToSlot;
        }
    }

    /**
     * Подменяем только те плейсхолдеры, которые реально были вставлены ItemPicker'ом.
     * Защита: вручную набранный токен [[item:id=...]] не сработает, потому что его id не будет в itemIdToSlot.
     */
    private PreparedOutgoing prepareOutgoingMessage(String rawText) {
        if (rawText == null || rawText.isEmpty() || pendingItems.isEmpty()) {
            return new PreparedOutgoing(rawText, Map.of());
        }

        // С конца, чтобы не ломать индексы
        pendingItems.sort(Comparator.comparingInt((PendingItem p) -> p.start).reversed());

        String out = rawText;
        Map<String, Integer> map = new HashMap<>();

        for (PendingItem p : pendingItems) {
            if (p.start < 0 || p.end > out.length() || p.start >= p.end) continue;

            // Доп. защита: заменяем только если под диапазоном всё ещё лежит ожидаемый placeholder
            String cur = out.substring(p.start, p.end);
            if (!cur.equals(p.placeholder)) continue;

            String token = "[[item:id=" + p.id + "]]";
            out = out.substring(0, p.start) + token + out.substring(p.end);

            map.put(p.id, p.slotMain);
        }

        return new PreparedOutgoing(out, map);
    }

    private void handleTabClick(double mouseX, double mouseY, int x, int y) {
        String[] tabs = {"Общий", "Торговый", "Личное", "Урон"};

        int tabX = x + 8;
        int tabPadding = 8;

        for (String tab : tabs) {
            int textWidth = this.font.width(tab);
            int btnW = textWidth + tabPadding * 2;
            int btnH = TAB_HEIGHT;

            if (mouseX >= tabX && mouseX < tabX + btnW && mouseY >= y && mouseY < y + btnH) {
                if (!Objects.equals(currentTab, tab)) {
                    this.currentTab = tab;
                    chatManager.setActiveTabName(tab);
                    this.scrollOffset = 0;

                    this.input.setValue("");
                    clearPendingItems();
                    lastInputValue = this.input.getValue();

                    if (this.customCommandSuggestions != null) {
                        this.customCommandSuggestions.updateCommandInfo();
                    }
                    invalidateWrappedCache();
                }
                break;
            }

            tabX += btnW + 4;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 0) ESC закрывает контекст-меню
        if (currentContextMenu != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            currentContextMenu = null;
            return true;
        }

        // 1) TAB / Ctrl+TAB
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            // Ctrl+TAB => подсказки команд
            if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                if (this.customCommandSuggestions != null) {
                    // Убираем CTRL, чтобы CommandSuggestions обработал это как обычный TAB
                    int modsNoCtrl = modifiers & ~GLFW.GLFW_MOD_CONTROL;
                    return this.customCommandSuggestions.keyPressed(keyCode, scanCode, modsNoCtrl);
                }
                return true;
            }

            // TAB hold: открыть item-picker на нажатии, закрыть на отпускании (см. keyReleased)
            if (!tabHeld) {
                tabHeld = true;

                int chatWidth = chatW();
                int chatHeight = chatH();
                int chatX = chatX();
                int chatY = chatY();

                // не используем toggle без защиты — иначе авто-repeat будет мигать
                if (!itemPicker.isOpen()) {
                    itemPicker.toggle(chatX, chatY, chatWidth, chatHeight, this.width, this.height);
                }
            }
            return true;
        }

        // 2) Когда item-picker открыт — часть клавиш должна уходить в него
        if (itemPicker.isOpen()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                itemPicker.close();
                tabHeld = false; // на случай, если игрок нажал ESC не отпуская TAB
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                itemPicker.insertSelected(this.input);
                return true;
            }

            if (itemPicker.keyPressed(keyCode)) {
                return true;
            }
        }

        // 3) Подсказки команд
        if (this.customCommandSuggestions != null) {
            if (this.customCommandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // 4) ENTER отправляет сообщение
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = this.input.getValue().trim();
            if (!text.isEmpty()) {
                sendMessageByTab(text);

                if (this.minecraft != null) {
                    this.minecraft.gui.getChat().addRecentChat(text);
                    this.minecraft.setScreen(null);
                }

                this.input.setValue("");
                clearPendingItems();
                lastInputValue = this.input.getValue();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // TAB hold: закрываем item-picker при отпускании TAB
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (tabHeld) {
                tabHeld = false;
                if (itemPicker.isOpen()) {
                    itemPicker.close();
                }
                return true;
            }
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    private void sendMessageByTab(String text) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) return;

        // "Урон": read-only
        if ("Урон".equals(currentTab)) {
            return;
        }

        // "Личное": только команды
        if ("Личное".equals(currentTab)) {
            if (text.startsWith("/")) {
                this.minecraft.getConnection().sendCommand(text.substring(1));
            } else {
                ClientChatManager.getInstance().addMessage(
                        ChatMessageType.GENERAL,
                        Component.literal("§c[!] Во вкладке 'Личное' можно писать только команды (напр. /msg Nick).")
                );
            }
            return;
        }

        // "Торговый"
        if ("Торговый".equals(currentTab)) {
            if (text.startsWith("/")) {
                this.minecraft.getConnection().sendCommand(text.substring(1));
            } else {
                PreparedOutgoing po = prepareOutgoingMessage(text);
                NetworkHandler.CHANNEL.sendToServer(new ChatMessagePacket(po.text, ChannelType.TRADE, po.itemIdToSlot));
            }
            return;
        }

        // Остальные вкладки
        if (text.startsWith("/")) {
            this.minecraft.getConnection().sendCommand(text.substring(1));
        } else {
            PreparedOutgoing po = prepareOutgoingMessage(text);
            NetworkHandler.CHANNEL.sendToServer(new ChatMessagePacket(po.text, ChannelType.ALL, po.itemIdToSlot));
        }
    }

    // ---------------------------
    // ИЗМЕНЕНИЕ: скролл подсказок
    // ---------------------------
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 1) Сначала отдаём колёсико подсказкам команд
        if (delta != 0 && this.customCommandSuggestions != null && isInsideChatPanel(mouseX, mouseY)) {
            if (invokeCommandSuggestionsMouseScrolled(this.customCommandSuggestions, mouseX, mouseY, delta)) {
                return true;
            }
        }

        // 2) Затем скроллим чат (только если курсор в зоне сообщений)
        if (delta != 0 && isInsideMessagesArea(mouseX, mouseY)) {
            this.scrollOffset += (delta > 0) ? 1 : -1;
            if (this.scrollOffset < 0) this.scrollOffset = 0;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * Безопасный вызов CommandSuggestions#mouseScrolled для разных маппингов:
     * - mouseScrolled(double mouseX, double mouseY, double delta)
     * - mouseScrolled(double delta)
     */
    private static boolean invokeCommandSuggestionsMouseScrolled(CommandSuggestions cs, double mouseX, double mouseY, double delta) {
        try {
            // вариант: (double, double, double)
            Method m = cs.getClass().getMethod("mouseScrolled", double.class, double.class, double.class);
            Object r = m.invoke(cs, mouseX, mouseY, delta);
            return (r instanceof Boolean) && (Boolean) r;
        } catch (NoSuchMethodException ignored) {
            // fallback ниже
        } catch (Throwable t) {
            return false;
        }

        try {
            // вариант: (double)
            Method m = cs.getClass().getMethod("mouseScrolled", double.class);
            Object r = m.invoke(cs, delta);
            return (r instanceof Boolean) && (Boolean) r;
        } catch (Throwable t) {
            return false;
        }
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

            buttons.add(new ContextButton("Профиль", () -> openProfile(targetNick)));
            buttons.add(new ContextButton("Написать ЛС", () -> openPrivateChat(targetNick)));
            buttons.add(new ContextButton("В друзья", () -> sendCommand("friend add " + targetNick)));
            buttons.add(new ContextButton("В пати", () -> sendCommand("party invite " + targetNick)));

            String ignoreText = ClientChatManager.getInstance().isPlayerIgnored(targetNick)
                    ? "Разблокировать"
                    : "Игнорировать";
            buttons.add(new ContextButton(ignoreText, () -> ignorePlayer(targetNick)));

            int totalHeight = buttons.size() * buttonHeight + 14;
            int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

            int yCandidate = mouseY;
            if (yCandidate + totalHeight > screenHeight) {
                yCandidate = mouseY - totalHeight;
            }
            this.renderY = Math.max(0, yCandidate);
        }

        public void render(GuiGraphics graphics, int mouseX, int mouseY, Font font) {
            // рисуем меню на повышенном Z-слое
            graphics.pose().pushPose();
            graphics.pose().translate(0.0D, 0.0D, 500.0D);

            int height = buttons.size() * buttonHeight + 14;

            // фон меню
            graphics.setColor(1f, 1f, 1f, 1.0f);
            ChatNineSlice.blit9(graphics, ChatTextures.CONTEXT_9, x, renderY, width, height, 8, 32, 32);

            // заголовок
            graphics.drawCenteredString(font, Component.literal(targetNick), x + width / 2, renderY + 3, 0xFFFFFF55);
            graphics.hLine(x + 4, x + width - 5, renderY + 13, 0x55FFFFFF);

            int currentY = renderY + 14;

            for (ContextButton btn : buttons) {
                boolean hovered = mouseX >= x && mouseX < x + width
                        && mouseY >= currentY && mouseY < currentY + buttonHeight;

                if (hovered) {
                    graphics.fill(x + 2, currentY, x + width - 2, currentY + buttonHeight, 0x3300E5FF);
                }

                graphics.drawString(font, btn.text, x + 6, currentY + 3, 0xFFFFFFFF, false);
                currentY += buttonHeight;
            }

            graphics.pose().popPose();
        }

        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) return false;

            int currentY = renderY + 14;
            for (ContextButton btn : buttons) {
                if (mouseX >= x && mouseX < x + width
                        && mouseY >= currentY && mouseY < currentY + buttonHeight) {

                    btn.action.run();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                    );
                    return true;
                }
                currentY += buttonHeight;
            }

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
