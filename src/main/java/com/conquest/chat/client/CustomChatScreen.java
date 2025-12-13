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
import java.util.Objects;

import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;
import com.conquest.chat.channel.ChannelType;
import com.conquest.chat.network.ChatMessagePacket;
import com.conquest.chat.network.NetworkHandler;

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

        // Hook: item-picker сообщает о вставке диапазона в input
        this.itemPicker.setOnInsert(this::onItemInserted);


        this.input.setResponder((text) -> {

            // Поддерживаем привязанные диапазоны предметов
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
        if (this.customCommandSuggestions != null) {
            this.customCommandSuggestions.updateCommandInfo();
        }
        invalidateWrappedCache();
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

        // input + suggestions (кроме "Урон")
        if (!"Урон".equals(currentTab)) {
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

        // overlay всегда поверх (не внутри suggestions)
        if (itemPicker.isOpen()) {
            int chatWidth = ChatConfig.CLIENT.chatWidth.get();
            int chatHeight = ChatConfig.CLIENT.chatHeight.get();
            int chatX = 4;
            int chatY = this.height - chatHeight - 4;

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

            graphics.fill(scrollBarX, y, scrollBarX + SCROLLBAR_WIDTH, y + trackHeight, 0x44000000);
            graphics.fill(scrollBarX, scrollY, scrollBarX + SCROLLBAR_WIDTH, scrollY + scrollH, 0xFFAAAAAA);
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
                cachedWrappedLines.addAll(this.font.split(msg, maxTextWidth));
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
        graphics.drawString(this.font, Component.literal("⚙"), x + 4, y + 3, 0xFFAAAAAA, false);
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

        // 3) Suggestions
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

        // Клик по "⚙" (пока заглушка)
        if (button == 0) {
            if (mouseX >= x + 4 && mouseX <= x + 12 && mouseY >= y + 3 && mouseY <= y + 11) {
                return true;
            }
        }

        // Клик по вкладкам
        if (button == 0) {
            if (mouseY >= y && mouseY < y + TAB_HEIGHT && mouseX >= x && mouseX < x + width) {
                handleTabClick(mouseX, x, y);
                return true;
            }
        }

        // Клики по области сообщений
        if (mouseY >= y + TAB_HEIGHT + 2 && mouseY <= y + height - 16) {
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
     * Все остальные SUGGEST_COMMAND (например "/friend add ..." или "/party invite ...") игнорируются.
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

        if (this.customCommandSuggestions != null) this.customCommandSuggestions.updateCommandInfo();
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
        pendingItems.add(new PendingItem(inserted.start(), inserted.end(), inserted.slotMain(), inserted.placeholder(), newItemId()));
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

        // ВАЖНО: переменные, используемые в lambda, должны быть effectively final
        final int prefixF = prefix;
        final int oldSuffixF = oldSuffix;
        final int deltaF = delta;

        // Обновляем диапазоны
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

    private String applyPendingItemsToMessage(String rawText) {

        if (rawText == null || rawText.isEmpty()) return rawText;
        if (pendingItems.isEmpty()) return rawText;

        // С конца, чтобы не ломать индексы
        pendingItems.sort(Comparator.comparingInt((PendingItem p) -> p.start).reversed());

        String out = rawText;

        for (PendingItem p : pendingItems) {
            if (p.start < 0 || p.end > out.length() || p.start >= p.end) continue;

            // Доп. защита: заменяем только если под диапазоном всё ещё лежит ожидаемый placeholder
            String cur = out.substring(p.start, p.end);
            if (!cur.equals(p.placeholder)) continue;

            String token = "[[item:inv=main,slot=" + p.slotMain + "]]";
            out = out.substring(0, p.start) + token + out.substring(p.end);
        }

        return out;

    }

    private void handleTabClick(double mouseX, int x, int y) {
        String[] tabs = {"Общий", "Торговый", "Личное", "Урон"};
        int tabX = x + 24;
        int tabPadding = 8;

        for (String tab : tabs) {
            int textWidth = this.font.width(tab);
            if (mouseX >= tabX && mouseX < tabX + textWidth + tabPadding * 2) {
                if (!Objects.equals(currentTab, tab)) {
                    this.currentTab = tab;
                    chatManager.setActiveTabName(tab);
                    this.scrollOffset = 0;
                    this.input.setValue("");

                    clearPendingItems();
                    lastInputValue = this.input.getValue();

                    if (this.customCommandSuggestions != null) this.customCommandSuggestions.updateCommandInfo();
                    invalidateWrappedCache();
                }
                break;
            }
            tabX += textWidth + tabPadding * 2;
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

                int chatWidth = ChatConfig.CLIENT.chatWidth.get();
                int chatHeight = ChatConfig.CLIENT.chatHeight.get();
                int chatX = 4;
                int chatY = this.height - chatHeight - 4;

                // ВАЖНО: не используем toggle без защиты — иначе авто-repeat клавиши будет мигать.
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

        // 3) Подсказки команд (обычное поведение, но после обработки TAB сверху)
        if (this.customCommandSuggestions != null) {
            if (this.customCommandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // 4) ENTER отправляет сообщение (как было)
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            String text = this.input.getValue().trim();
            if (!text.isEmpty()) {
                sendMessageByTab(text);
                this.minecraft.gui.getChat().addRecentChat(text);
                this.input.setValue("");

                clearPendingItems();
                lastInputValue = this.input.getValue();
                this.minecraft.setScreen(null);
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
            }
            if (itemPicker.isOpen()) {
                itemPicker.close();
            }
            return true;
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

        // "Торговый": добавляем префикс, если не команда
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

        public void render(GuiGraphics graphics, int mouseX, int mouseY, net.minecraft.client.gui.Font font) {
            // рисуем меню на повышенном Z-слое
            graphics.pose().pushPose();
            graphics.pose().translate(0.0D, 0.0D, 500.0D);

            int height = buttons.size() * buttonHeight + 14;

            graphics.fill(x, renderY, x + width, renderY + height, 0xFF000000);
            graphics.renderOutline(x, renderY, width, height, 0xFFFFFFFF);

            graphics.drawCenteredString(font, Component.literal(targetNick), x + width / 2, renderY + 3, 0xFFFFFF00);
            graphics.fill(x, renderY + 13, x + width, renderY + 14, 0xFF555555);

            int currentY = renderY + 14;
            for (ContextButton btn : buttons) {
                boolean hovered = mouseX >= x && mouseX < x + width
                        && mouseY >= currentY && mouseY < currentY + buttonHeight;

                int bgColor = hovered ? 0xFF444444 : 0x00000000;
                graphics.fill(x + 1, currentY, x + width - 1, currentY + buttonHeight, bgColor);
                graphics.drawString(font, btn.text, x + 5, currentY + 3, 0xFFFFFFFF, false);

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