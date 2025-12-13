package com.conquest.chat.client;

import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientChatManager {

    private static ClientChatManager instance;

    private final Map<String, List<Component>> tabMessages;
    private final Set<String> ignoredPlayers = new HashSet<>();

    private String activeTabName;

    private static final int MAX_HISTORY = 100;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[\\w\\.\\-]+\\w{2,}(/[\\w\\.\\-?&=%+]*)?");

    private static final Pattern NICK_PATTERN =
            Pattern.compile("(?<=<|\\s|^)([a-zA-Z0-9_]{3,16})(?=:|>|\\s)");

    // ---------------------------
    // HUD (когда чат закрыт)
    // ---------------------------
    private static final int MAX_HUD = 80;
    private final Deque<HudEntry> hudMessages = new ArrayDeque<>();

    private record HudEntry(String tab, Component msg, long timeMs) {}

    private ClientChatManager() {
        tabMessages = new HashMap<>();
        tabMessages.put("Общий", new ArrayList<>());
        tabMessages.put("Торговый", new ArrayList<>());
        tabMessages.put("Личное", new ArrayList<>());
        tabMessages.put("Урон", new ArrayList<>());

        activeTabName = "Общий";
    }

    public static ClientChatManager getInstance() {
        if (instance == null) {
            instance = new ClientChatManager();
        }
        return instance;
    }

    public String getActiveTabName() {
        return activeTabName;
    }

    public void setActiveTabName(String tabName) {
        this.activeTabName = tabName;
    }

    public boolean isPlayerIgnored(String playerName) {
        return ignoredPlayers.contains(playerName);
    }

    public void toggleIgnorePlayer(String playerName) {
        if (ignoredPlayers.contains(playerName)) {
            ignoredPlayers.remove(playerName);
            addMessage(ChatMessageType.SYSTEM,
                    Component.literal("§a[!] Игрок " + playerName + " удален из черного списка."));
        } else {
            ignoredPlayers.add(playerName);
            addMessage(ChatMessageType.SYSTEM,
                    Component.literal("§c[!] Игрок " + playerName + " добавлен в черный список."));
        }
    }

    public void addMessage(ChatMessageType type, Component message) {
        if (Minecraft.getInstance().player == null) return;

        String rawText = message.getString();

        // 1) Фильтр игнора
        for (String ignored : ignoredPlayers) {
            if (rawText.contains(ignored)) {
                if (rawText.startsWith("<" + ignored + ">")
                        || rawText.startsWith(ignored + ":")
                        || rawText.contains("]" + ignored + ":")) {
                    return;
                }
            }
        }

        // 2) Определение категории
        boolean isDamage = (rawText.contains("[Бой]") || rawText.contains("[COMBAT]")
                || rawText.contains("урона") || rawText.contains("Вы убили"));

        boolean isTrade = type == ChatMessageType.TRADE || rawText.contains("[Торг]");

        // --- расширенная проверка личных сообщений ---
        boolean isPrivate = type == ChatMessageType.PRIVATE
                || rawText.contains("[Личное]")
                || rawText.contains(" шепчет")
                || rawText.contains(" whispers")
                || rawText.contains(" -> ");

        // 3) Агрессивная очистка от серверных тегов
        MutableComponent contentCopy = message.copy();

        // === ВАЖНО: замена COMBAT -> Бой (и для чата, и для HUD) ===
        contentCopy = replaceTextInComponent(contentCopy, "[COMBAT]", "[Бой]");

        if (isTrade) {
            contentCopy = removeTextFromComponent(contentCopy, "[Торг]");
            contentCopy = removeTextFromComponent(contentCopy, "[Торг] ");
        }

        if (isPrivate) {
            contentCopy = removeTextFromComponent(contentCopy, "[Личное]");
            contentCopy = removeTextFromComponent(contentCopy, "[Личное] ");
        }

        // 4) Линки/ники
        MutableComponent processedContent = processComponentText(contentCopy);

        // 5) Префикс
        String prefixText = "";
        ChatFormatting prefixColor = ChatFormatting.GRAY;

        if (isDamage) {
            prefixText = "";
        } else if (isTrade) {
            prefixText = "[Торговый] ";
            prefixColor = ChatFormatting.GOLD;
        } else if (isPrivate) {
            prefixText = "[Личное] ";
            prefixColor = ChatFormatting.LIGHT_PURPLE;
        } else if (type == ChatMessageType.SYSTEM) {
            prefixText = "[Система] ";
            prefixColor = ChatFormatting.RED;
        } else {
            String cleanText = ChatFormatting.stripFormatting(rawText);
            cleanText = (cleanText == null) ? "" : cleanText.trim();

            boolean hasPrefix = cleanText.startsWith("[");
            if (!hasPrefix) {
                prefixText = "[Общий] ";
                prefixColor = ChatFormatting.WHITE;

                // “без ника” чаще всего система
                if (!rawText.contains("<") && !rawText.contains(": ")) {
                    prefixText = "[Система] ";
                    prefixColor = ChatFormatting.YELLOW;
                }
            }
        }

        // 6) Сборка финального компонента
        MutableComponent finalMessage = Component.empty();

        finalMessage.append(
                Component.literal("[" + LocalTime.now().format(TIME_FORMATTER) + "] ")
                        .withStyle(ChatFormatting.GRAY)
        );

        if (!prefixText.isEmpty()) {
            finalMessage.append(Component.literal(prefixText).withStyle(prefixColor));
        }

        finalMessage.append(processedContent);

        // 7) Маршрутизация
        if (isDamage) {
            addToTab("Урон", finalMessage);
            addToTab("Общий", finalMessage);
        } else {
            if (isTrade) addToTab("Торговый", finalMessage);
            if (isPrivate) addToTab("Личное", finalMessage);
            addToTab("Общий", finalMessage);
        }
    }

    private MutableComponent removeTextFromComponent(MutableComponent comp, String target) {
        if (comp == null) return Component.empty();

        if (comp.getContents() instanceof TranslatableContents) {
            String text = comp.getString();
            if (text.contains(target)) {
                String newText = text.replace(target, "");
                return Component.literal(newText).setStyle(comp.getStyle());
            }
        }

        if (comp.getContents() instanceof LiteralContents) {
            String literalText = comp.getString();
            if (literalText.contains(target)) {
                String newText = literalText.replace(target, "");
                MutableComponent newComp = Component.literal(newText).setStyle(comp.getStyle());
                for (Component child : comp.getSiblings()) {
                    newComp.append(removeTextFromComponent(child.copy(), target));
                }
                return newComp;
            }
        }

        MutableComponent newComp = comp.plainCopy().setStyle(comp.getStyle());
        for (Component child : comp.getSiblings()) {
            newComp.append(removeTextFromComponent(child.copy(), target));
        }
        return newComp;
    }

    private MutableComponent replaceTextInComponent(MutableComponent comp, String from, String to) {
        MutableComponent out;

        // Меняем только literal-часть текущего компонента, стиль сохраняем
        if (comp.getContents() instanceof LiteralContents lc) {
            String t = lc.text();
            out = Component.literal(t.replace(from, to)).setStyle(comp.getStyle());
        } else {
            out = comp.plainCopy().setStyle(comp.getStyle());
        }

        // Рекурсивно обрабатываем siblings
        for (Component child : comp.getSiblings()) {
            if (child instanceof MutableComponent mc) {
                out.append(replaceTextInComponent(mc, from, to));
            } else {
                out.append(child);
            }
        }

        return out;
    }

    private void addToTab(String tabName, Component msg) {
        List<Component> list = tabMessages.get(tabName);
        if (list == null) return;

        if (!list.isEmpty() && list.get(list.size() - 1) == msg) return;

        list.add(msg);
        if (list.size() > MAX_HISTORY) list.remove(0);

        // HUD: сохраняем сообщения ПО ВКЛАДКЕ (для фильтрации по activeTabName)
        hudMessages.addLast(new HudEntry(tabName, msg, System.currentTimeMillis()));
        while (hudMessages.size() > MAX_HUD) hudMessages.pollFirst();
    }

    public List<Component> getMessages(String tabName) {
        return tabMessages.getOrDefault(tabName, Collections.emptyList());
    }

    /**
     * Метод, который ожидает ClientEventHandler:
     * возвращает HUD-сообщения ТОЛЬКО для активной вкладки.
     */
    public List<Component> getHudMessages(int maxLines, long lifetimeMs) {
        long now = System.currentTimeMillis();

        // Чистим старое по времени (глобально, т.к. очередь общая)
        while (!hudMessages.isEmpty() && (now - hudMessages.peekFirst().timeMs) > lifetimeMs) {
            hudMessages.pollFirst();
        }

        String tab = (activeTabName == null || activeTabName.isEmpty()) ? "Общий" : activeTabName;

        List<Component> out = new ArrayList<>();
        Iterator<HudEntry> it = hudMessages.descendingIterator();

        while (it.hasNext() && out.size() < maxLines) {
            HudEntry e = it.next();
            if (tab.equals(e.tab)) {
                out.add(e.msg);
            }
        }

        Collections.reverse(out);
        return out;
    }

    public void clear() {
        tabMessages.values().forEach(List::clear);
        hudMessages.clear();
    }

    private MutableComponent processComponentText(MutableComponent comp) {
        Style style = comp.getStyle();
        if (style != null && style.getClickEvent() != null) return comp;

        List<Component> siblings = comp.getSiblings();
        if (!siblings.isEmpty()) {
            MutableComponent newComp = comp.plainCopy().setStyle(style);
            for (Component child : siblings) {
                if (child instanceof MutableComponent mc) {
                    newComp.append(processComponentText(mc));
                } else {
                    newComp.append(child);
                }
            }
            return newComp;
        }

        String text = comp.getString();
        if (text.isEmpty()) return comp;

        // Не ломаем уже размеченные сообщения
        if (text.contains("[Бой]") || text.trim().startsWith("[")) {
            return Component.literal(text).withStyle(style);
        }

        // ==========================
        // FPS/GC: быстрые выходы
        // ==========================
        boolean mayContainUrl = text.indexOf("http://") >= 0 || text.indexOf("https://") >= 0;

        // Ники ожидаются в форматах "<Nick>" / "Nick:" / "... Nick ..."
        // (в контексте твоего NICK_PATTERN lookbehind/lookahead) — если нет ни одного из маркеров,
        // можно пропускать regex и вернуть literal.
        boolean mayContainNickMarkers = (text.indexOf('<') >= 0) || (text.indexOf('>') >= 0) || (text.indexOf(':') >= 0);

        if (!mayContainUrl && !mayContainNickMarkers) {
            return Component.literal(text).withStyle(style);
        }

        // URL
        if (mayContainUrl) {
            Matcher urlMatcher = URL_PATTERN.matcher(text);
            if (urlMatcher.find()) {
                urlMatcher.reset();

                List<Component> parts = new ArrayList<>();
                int lastIdx = 0;

                while (urlMatcher.find()) {
                    String before = text.substring(lastIdx, urlMatcher.start());
                    if (!before.isEmpty()) parts.add(Component.literal(before).withStyle(style));

                    String url = urlMatcher.group();
                    parts.add(Component.literal(url).withStyle(
                            style.withColor(ChatFormatting.BLUE)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    ));

                    lastIdx = urlMatcher.end();
                }

                if (lastIdx < text.length()) {
                    parts.add(Component.literal(text.substring(lastIdx)).withStyle(style));
                }

                MutableComponent result = Component.empty();
                for (Component p : parts) result.append(p);
                return result;
            }
        }

        // Ники
        if (mayContainNickMarkers) {
            Matcher nickMatcher = NICK_PATTERN.matcher(text);
            if (nickMatcher.find()) {
                nickMatcher.reset();

                List<Component> parts = new ArrayList<>();
                int lastIdx = 0;
                boolean found = false;

                while (nickMatcher.find()) {
                    String nick = nickMatcher.group(1);

                    // отсев “шумных” матчей
                    if (nick.equals(nick.toUpperCase()) && nick.length() > 4) continue;

                    found = true;

                    String before = text.substring(lastIdx, nickMatcher.start());
                    if (!before.isEmpty()) parts.add(Component.literal(before).withStyle(style));

                    Style nickStyle = style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/w " + nick + " "))
                            .withHoverEvent(new HoverEvent(
                                    HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("§eПКМ: Меню\n§7ЛКМ: Упомянуть")
                            ));

                    parts.add(Component.literal(nick).withStyle(nickStyle));
                    lastIdx = nickMatcher.end();
                }

                if (found) {
                    if (lastIdx < text.length()) {
                        parts.add(Component.literal(text.substring(lastIdx)).withStyle(style));
                    }

                    MutableComponent result = Component.empty();
                    for (Component p : parts) result.append(p);
                    return result;
                }
            }
        }

        return Component.literal(text).withStyle(style);
    }
}
