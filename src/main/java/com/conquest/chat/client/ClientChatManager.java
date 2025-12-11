package com.conquest.chat.client;

import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

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

    // Regex для урона
    private static final Pattern DAMAGE_DEALT_PATTERN = Pattern.compile("Вы нанесли ([0-9.]+) урона по (.+)");
    private static final Pattern KILL_PATTERN = Pattern.compile("Вы убили (.+)");

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
        String originalText = originalMessage.getString();

        // --- 1. ОПРЕДЕЛЕНИЕ КАНАЛА И ТИПА (Парсинг) ---
        ChatChannel detectedChannel = ChatChannel.ALL;
        ChatFormatting color = ChatFormatting.WHITE;
        String prefix = "";

        // Логика определения типа сообщения
        if (type == ChatMessageType.COMBAT || originalText.startsWith("[COMBAT]") || DAMAGE_DEALT_PATTERN.matcher(originalText).find()) {
            detectedChannel = ChatChannel.COMBAT;
        } else if (originalText.contains("[Торг]") || type == ChatMessageType.TRADE) {
            detectedChannel = ChatChannel.TRADE;
            color = ChatFormatting.BLUE; // &9
            // Если префикса еще нет в тексте (например, мы сами отправляем), добавим его визуально
            if (!originalText.contains("[Торг]")) prefix = "[Торг] ";
        } else if (originalText.contains("шепчет") || originalText.contains("прошептали") || type == ChatMessageType.WHISPER) {
            detectedChannel = ChatChannel.WHISPER;
            color = ChatFormatting.DARK_PURPLE; // &5
            if (!originalText.contains("[Личное]")) prefix = "[Личное] ";
        } else {
            // Обычное сообщение
            detectedChannel = ChatChannel.ALL;
            color = ChatFormatting.WHITE;
            // Не добавляем префикс [Общий], если это просто чат, чтобы не захламлять
            // prefix = "[Общий] ";
        }

        // --- 2. ФОРМАТИРОВАНИЕ ---
        Component finalMessage;

        // Создаем final переменную для использования в лямбде
        final ChatFormatting finalColor = color;

        if (detectedChannel == ChatChannel.COMBAT) {
            finalMessage = reformatCombatMessage(originalMessage);
        } else {
            // Формируем: [HH:mm:ss] [Prefix] OriginalMessage (с цветом)
            String time = "[" + LocalTime.now().format(TIME_FORMATTER) + "] ";

            MutableComponent timeComp = Component.literal(time).withStyle(ChatFormatting.GRAY);
            MutableComponent prefixComp = Component.literal(prefix).withStyle(finalColor);

            // Если сообщение уже имеет стиль, пытаемся его сохранить, иначе накладываем цвет канала
            MutableComponent contentComp = originalMessage.copy().withStyle(style -> {
                // Используем finalColor вместо color
                if (style.getColor() == null) return style.applyFormat(finalColor);
                return style;
            });

            finalMessage = timeComp.append(prefixComp).append(contentComp);
        }

        // --- 3. РАСПРЕДЕЛЕНИЕ ПО ТАБАМ ---

        // Всегда добавляем в свой канал
        messages.computeIfAbsent(detectedChannel, k -> new ArrayList<>()).add(finalMessage);

        // Если это НЕ общий канал, дублируем в Общий (с сохранением цвета оригинала!)
        if (detectedChannel != ChatChannel.ALL) {
            messages.computeIfAbsent(ChatChannel.ALL, k -> new ArrayList<>()).add(finalMessage);
        }

        trimHistory(ChatChannel.ALL);
        trimHistory(detectedChannel);
    }

    private Component reformatCombatMessage(Component msg) {
        String text = msg.getString().replace("[COMBAT] ", "").trim();
        String time = LocalTime.now().format(TIME_FORMATTER);

        Matcher mDealt = DAMAGE_DEALT_PATTERN.matcher(text);
        if (mDealt.find()) {
            String dmg = mDealt.group(1);
            String entity = mDealt.group(2);
            String fmt = String.format("[%s] [Урон. Исходящий]: Вы ранили %s (%s)", time, entity, dmg);
            return Component.literal(fmt).withStyle(ChatFormatting.RED);
        }

        Matcher mKill = KILL_PATTERN.matcher(text);
        if (mKill.find()) {
            String entity = mKill.group(1);
            String fmt = String.format("[%s] [Урон. Исходящий]: Вы убили %s", time, entity);
            return Component.literal(fmt).withStyle(ChatFormatting.GOLD);
        }

        return Component.literal("[" + time + "] " + text).withStyle(ChatFormatting.RED);
    }

    private void trimHistory(ChatChannel channel) {
        List<Component> list = messages.get(channel);
        if (list != null && list.size() > 100) list.remove(0);
    }

    // --- Геттеры и сеттеры ---
    public List<Component> getMessages(String channelName) {
        return getMessagesForTab(mapStringToChannel(channelName));
    }

    public List<Component> getMessagesForTab(ChatChannel tab) {
        ChatChannel target = (tab == null) ? activeTab : tab;
        return messages.getOrDefault(target, new ArrayList<>());
    }

    private ChatChannel mapStringToChannel(String name) {
        switch (name) {
            case "Торг": return ChatChannel.TRADE;
            case "Личное": return ChatChannel.WHISPER;
            case "Урон": return ChatChannel.COMBAT;
            case "Общий": default: return ChatChannel.ALL;
        }
    }

    private ChatChannel mapTypeToChannel(ChatMessageType type) {
        if (type == ChatMessageType.TRADE) return ChatChannel.TRADE;
        if (type == ChatMessageType.WHISPER) return ChatChannel.WHISPER;
        if (type == ChatMessageType.COMBAT) return ChatChannel.COMBAT;
        return ChatChannel.ALL;
    }

    public ChatChannel getActiveTab() { return activeTab; }
    public void setActiveTab(ChatChannel t) { this.activeTab = t; }

    public ChatMessageType getOutgoingType() {
        if (activeTab == ChatChannel.TRADE) return ChatMessageType.TRADE;
        if (activeTab == ChatChannel.WHISPER) return ChatMessageType.WHISPER;
        return ChatMessageType.GENERAL;
    }

    public void clear() { messages.values().forEach(List::clear); }
}
