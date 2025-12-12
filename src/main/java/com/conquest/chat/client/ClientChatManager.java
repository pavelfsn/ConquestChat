package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientChatManager {
    private static ClientChatManager instance;
    private final Map<ChatChannel, List<Component>> messages = new HashMap<>();
    private ChatChannel activeTab = ChatChannel.ALL;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Паттерны
    private static final Pattern DAMAGE_DEALT_PATTERN = Pattern.compile("Вы нанесли ([0-9.]+) урона по (.+)");
    private static final Pattern DAMAGE_TAKEN_PATTERN = Pattern.compile("Вы получили ([0-9.]+) урона от (.+)");
    private static final Pattern KILL_PATTERN = Pattern.compile("Вы убили (.+)");
    private static final Pattern EXISTING_TIME_PATTERN = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\]");

    // УЛУЧШЕННЫЙ ПАТТЕРН НИКА: Ищет "Слово:", возможно с префиксом.
    // Пример: "[G] Nick:", "Nick:", "<Nick>"
    // Группа 1 - это сам ник.
    private static final Pattern NICKNAME_DETECTOR = Pattern.compile("(?<=^|\\s|\\[|\\<)([a-zA-Z0-9_]{3,16})(?=:|\\>|\\s)");

    private static final Pattern TRADE_TAG_PATTERN = Pattern.compile("\\[Торг\\]", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    private ClientChatManager() {
        for (ChatChannel channel : ChatChannel.values()) {
            messages.put(channel, new ArrayList<>());
        }
    }

    public static ClientChatManager getInstance() {
        if (instance == null) {
            instance = new ClientChatManager();
        }
        return instance;
    }

    public void addMessage(ChatMessageType type, Component originalMessage) {
        String cleanText = originalMessage.getString().trim();
        String lowerText = cleanText.toLowerCase();

        ConquestChatMod.LOGGER.info("[DEBUG ChatManager] Received: " + cleanText);

        ChatChannel detectedChannel = ChatChannel.ALL;
        ChatFormatting channelColor = ChatFormatting.WHITE;
        String prefix = "";
        boolean needCleanup = false;

        if (type == ChatMessageType.COMBAT || cleanText.startsWith("[COMBAT]") || DAMAGE_DEALT_PATTERN.matcher(cleanText).find() || DAMAGE_TAKEN_PATTERN.matcher(cleanText).find()) {
            detectedChannel = ChatChannel.COMBAT;
        } else if (lowerText.contains("[торг]") || type == ChatMessageType.TRADE) {
            detectedChannel = ChatChannel.TRADE;
            channelColor = ChatFormatting.BLUE;
            prefix = "[Торг] ";
            needCleanup = true;
        } else if (lowerText.contains("шепчет") || lowerText.contains("прошептали") || type == ChatMessageType.WHISPER) {
            detectedChannel = ChatChannel.WHISPER;
            channelColor = ChatFormatting.DARK_PURPLE;
        } else {
            detectedChannel = ChatChannel.ALL;
            channelColor = ChatFormatting.WHITE;
            if (!cleanText.startsWith("[Общий]")) prefix = "[Общий] ";
        }

        Component finalMessage;

        if (detectedChannel == ChatChannel.COMBAT) {
            finalMessage = reformatCombatMessage(cleanText);
        } else {
            MutableComponent messageBuilder = Component.empty();

            if (!EXISTING_TIME_PATTERN.matcher(cleanText).find()) {
                String timeStr = "[" + LocalTime.now().format(TIME_FORMATTER) + "] ";
                messageBuilder.append(Component.literal(timeStr).withStyle(ChatFormatting.GRAY));
            }

            final ChatFormatting finalColor = channelColor;
            if (!prefix.isEmpty()) {
                messageBuilder.append(Component.literal(prefix).withStyle(finalColor));
            }

            MutableComponent contentComp;
            if (needCleanup) {
                String textContent = originalMessage.getString();
                String cleanedContent = TRADE_TAG_PATTERN.matcher(textContent).replaceAll("").trim();
                cleanedContent = cleanedContent.replaceAll("  +", " ");
                contentComp = Component.literal(cleanedContent); // ТУТ ТЕРЯЕМ ССЫЛКИ
            } else {
                contentComp = originalMessage.copy();
            }

            // ВОССТАНАВЛИВАЕМ ИНТЕРАКТИВНОСТЬ (Ссылки + Ники)
            if (detectedChannel != ChatChannel.COMBAT) {
                contentComp = processInteractivity(contentComp);
            }

            if (detectedChannel != ChatChannel.ALL) {
                contentComp = applyStyleRecursive(contentComp, finalColor);
            }

            messageBuilder.append(contentComp);
            finalMessage = messageBuilder;
        }

        messages.computeIfAbsent(detectedChannel, k -> new ArrayList<>()).add(finalMessage);

        if (detectedChannel != ChatChannel.ALL) {
            messages.computeIfAbsent(ChatChannel.ALL, k -> new ArrayList<>()).add(finalMessage);
        }

        if (detectedChannel == ChatChannel.WHISPER) {
            messages.computeIfAbsent(ChatChannel.TRADE, k -> new ArrayList<>()).add(finalMessage);
        }

        trimHistory(ChatChannel.ALL);
        trimHistory(detectedChannel);
    }

    // Новый метод обработки ссылок и ников
    private MutableComponent processInteractivity(MutableComponent root) {
        // Рекурсивно или плоско? Лучше плоско создать новый компонент, если текст содержит ссылки.
        // Но это сложно, если компонент уже имеет структуру.
        // Простой вариант: Пройтись по plain text и, если находим совпадения, разбивать на части.

        // Попробуем упрощенный вариант:
        // Если компонент - это просто текст без событий, проверим его.

        return processComponentText(root);
    }

    private MutableComponent processComponentText(MutableComponent comp) {
        String text = comp.getString();
        List<Component> siblings = comp.getSiblings();
        Style style = comp.getStyle();

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

        if (style.getClickEvent() != null) return comp;

        List<Component> parts = new ArrayList<>();
        // УПРОЩЕННЫЙ ПАТТЕРН: Ищем ник перед двоеточием или >
        // Группа 1: Ник
        Matcher nickMatcher = Pattern.compile("([a-zA-Z0-9_]{3,16})(?=:|>)").matcher(text);
        // Добавим URL
        Matcher urlMatcher = URL_PATTERN.matcher(text);

        // Приоритет URL
        if (urlMatcher.find()) {
            urlMatcher.reset();
            int lastIdx = 0;
            while (urlMatcher.find()) {
                String before = text.substring(lastIdx, urlMatcher.start());
                if (!before.isEmpty()) parts.add(Component.literal(before).withStyle(style));

                String url = urlMatcher.group();
                parts.add(Component.literal(url).withStyle(style.withColor(ChatFormatting.BLUE).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))));
                lastIdx = urlMatcher.end();
            }
            if (lastIdx < text.length()) parts.add(Component.literal(text.substring(lastIdx)).withStyle(style));

            MutableComponent result = Component.empty();
            for (Component p : parts) result.append(p);
            return result;
        }

        if (nickMatcher.find()) {
            ConquestChatMod.LOGGER.info("[DEBUG] Found nickname match in text: " + text);
            nickMatcher.reset();
            int lastIdx = 0;
            while (nickMatcher.find()) {
                String before = text.substring(lastIdx, nickMatcher.start());
                if (!before.isEmpty()) parts.add(Component.literal(before).withStyle(style));

                String nick = nickMatcher.group(1);

                // СТИЛЬ НИКА
                Style nickStyle = style
                        .withColor(ChatFormatting.YELLOW) // Для теста покрасим в желтый
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/w " + nick + " "))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("ПКМ: ЛС | ЛКМ: Упомянуть")));

                parts.add(Component.literal(nick).withStyle(nickStyle));

                lastIdx = nickMatcher.end(); // Мы берем только ник, двоеточие останется в след. части или будет съедено?
                // Паттерн (?=:) - это lookahead, он НЕ захватывает двоеточие. Оно останется в тексте.
            }
            if (lastIdx < text.length()) {
                parts.add(Component.literal(text.substring(lastIdx)).withStyle(style));
            }

            MutableComponent result = Component.empty();
            for (Component p : parts) result.append(p);
            return result;
        }

        return comp;
    }

    private MutableComponent applyStyleRecursive(MutableComponent comp, ChatFormatting color) {
        Style oldStyle = comp.getStyle();
        // Не перезаписываем цвет, если он уже есть и отличается от дефолтного?
        // Нет, требование - менять цвет канала. Но ссылки должны оставаться синими.
        if (oldStyle.getClickEvent() != null && oldStyle.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
            return comp; // Не красим ссылки в цвет канала
        }

        Style newStyle = oldStyle.applyFormat(color);
        MutableComponent newComp = comp.copy().setStyle(newStyle);
        newComp.getSiblings().clear(); // Очищаем старых детей, так как добавим новых рекурсивно

        for (Component child : comp.getSiblings()) {
            if (child instanceof MutableComponent mutableChild) {
                newComp.append(applyStyleRecursive(mutableChild, color));
            } else {
                newComp.append(child);
            }
        }
        return newComp;
    }

    private Component reformatCombatMessage(String text) {
        text = text.replace("[COMBAT] ", "").trim();
        String time = "[" + LocalTime.now().format(TIME_FORMATTER) + "] ";
        MutableComponent builder = Component.literal(time).withStyle(ChatFormatting.GRAY);

        Matcher mDealt = DAMAGE_DEALT_PATTERN.matcher(text);
        if (mDealt.find()) {
            String dmg = mDealt.group(1);
            String entity = mDealt.group(2);
            builder.append(Component.literal("[Урон. Исходящий]: ").withStyle(ChatFormatting.RED));
            builder.append(Component.literal("Вы ранили ").withStyle(ChatFormatting.RED));
            builder.append(Component.literal(entity).withStyle(ChatFormatting.YELLOW));
            builder.append(Component.literal(" (" + dmg + ")").withStyle(ChatFormatting.RED));
            return builder;
        }

        Matcher mTaken = DAMAGE_TAKEN_PATTERN.matcher(text);
        if (mTaken.find()) {
            String dmg = mTaken.group(1);
            String source = mTaken.group(2);
            builder.append(Component.literal("[Урон. Входящий]: ").withStyle(ChatFormatting.RED));
            builder.append(Component.literal("Вы получили ").withStyle(ChatFormatting.RED));
            builder.append(Component.literal("(" + dmg + ")").withStyle(ChatFormatting.YELLOW));
            builder.append(Component.literal(" ед. урона от ").withStyle(ChatFormatting.RED));
            builder.append(Component.literal(source).withStyle(ChatFormatting.YELLOW));
            return builder;
        }

        Matcher mKill = KILL_PATTERN.matcher(text);
        if (mKill.find()) {
            String entity = mKill.group(1);
            builder.append(Component.literal("[Урон. Исходящий]: ").withStyle(ChatFormatting.GOLD));
            builder.append(Component.literal("Вы убили ").withStyle(ChatFormatting.GOLD));
            builder.append(Component.literal(entity).withStyle(ChatFormatting.YELLOW));
            return builder;
        }

        return Component.literal(time + text).withStyle(ChatFormatting.RED);
    }

    private void trimHistory(ChatChannel channel) {
        List<Component> list = messages.get(channel);
        if (list != null && list.size() > 100) list.remove(0);
    }

    public List<Component> getMessages(String channelName) { return getMessagesForTab(mapStringToChannel(channelName)); }
    public List<Component> getMessagesForTab(ChatChannel tab) { ChatChannel target = (tab == null) ? activeTab : tab; return messages.getOrDefault(target, new ArrayList<>()); }

    private ChatChannel mapStringToChannel(String name) {
        switch (name) {
            case "Торг": return ChatChannel.TRADE;
            case "Личное": return ChatChannel.WHISPER;
            case "Урон": return ChatChannel.COMBAT;
            case "Общий": default: return ChatChannel.ALL;
        }
    }

    public void setActiveTabName(String name) { this.activeTab = mapStringToChannel(name); }
    public String getActiveTabName() {
        switch (activeTab) {
            case TRADE: return "Торг";
            case WHISPER: return "Личное";
            case COMBAT: return "Урон";
            default: return "Общий";
        }
    }
    public void clear() { messages.values().forEach(List::clear); }
}
