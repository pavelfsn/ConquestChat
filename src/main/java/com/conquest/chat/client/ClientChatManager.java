package com.conquest.chat.client;

import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;

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

    // Паттерны для перехвата стандартных сообщений (если сервер шлет их текстом)
    // Пример: "[COMBAT] Вы нанесли 0.9 урона по Слизень"
    private static final Pattern DAMAGE_DEALT_PATTERN = Pattern.compile("Вы нанесли ([0-9.]+) урона по (.+)");
    private static final Pattern DAMAGE_TAKEN_PATTERN = Pattern.compile("Вы получили ([0-9.]+) урона от (.+)");
    private static final Pattern KILL_PATTERN = Pattern.compile("Вы убили (.+)");
    private static final Pattern KILLED_BY_PATTERN = Pattern.compile("Вас убил (.+)");

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
        ChatChannel target = mapTypeToChannel(type);
        Component finalMessage = originalMessage;

        // Если это боевой чат, пытаемся переформатировать
        if (target == ChatChannel.COMBAT) {
            finalMessage = reformatCombatMessage(originalMessage);
        } else {
            // Для остальных добавляем время [HH:mm:ss] [Вкладка] [Игрок]: msg
            // Сложно распарсить "Игрока" из Component, если он там жестко зашит.
            // Поэтому просто добавляем время.
            String time = "[" + LocalTime.now().format(TIME_FORMATTER) + "] ";
            finalMessage = Component.literal(time).append(originalMessage);
        }

        // Дублируем в ALL
        messages.computeIfAbsent(ChatChannel.ALL, k -> new ArrayList<>()).add(finalMessage);

        if (target != ChatChannel.ALL) {
            messages.computeIfAbsent(target, k -> new ArrayList<>()).add(finalMessage);
        }

        trimHistory(ChatChannel.ALL);
        trimHistory(target);
    }

    private Component reformatCombatMessage(Component msg) {
        String text = msg.getString(); // Получаем сырой текст без цветов
        // Убираем префикс [COMBAT] если он есть в тексте
        text = text.replace("[COMBAT] ", "").trim();

        String time = LocalTime.now().format(TIME_FORMATTER);
        String player = "Вы"; // Предполагаем "Вы", так как логи клиентоориентированные

        Matcher mDealt = DAMAGE_DEALT_PATTERN.matcher(text);
        if (mDealt.find()) {
            String dmg = mDealt.group(1);
            String entity = mDealt.group(2);
            // [HH.MM.SS] [Урон. Исходящий] [Игрок]: Вы ранили [AnyEntity] (#.#)
            String fmt = String.format("[%s] [Урон. Исходящий] [%s]: Вы ранили %s (%s)", time, player, entity, dmg);
            return Component.literal(fmt).withStyle(ChatFormatting.GREEN);
        }

        Matcher mKill = KILL_PATTERN.matcher(text);
        if (mKill.find()) {
            String entity = mKill.group(1);
            String fmt = String.format("[%s] [Урон. Исходящий] [%s]: Вы убили: %s", time, player, entity);
            return Component.literal(fmt).withStyle(ChatFormatting.GOLD);
        }

        // Возвращаем как есть, если не подошло, но с временем
        return Component.literal("[" + time + "] " + text).withStyle(ChatFormatting.GRAY);
    }

    private void trimHistory(ChatChannel channel) {
        List<Component> list = messages.get(channel);
        if (list != null && list.size() > 100) list.remove(0);
    }

    public List<Component> getMessages(String channelName) {
        return getMessagesForTab(mapStringToChannel(channelName));
    }

    public List<Component> getMessagesForTab(ChatChannel tab) {
        ChatChannel target = (tab == null) ? activeTab : tab;
        return messages.getOrDefault(target, new ArrayList<>());
    }

    // ... Маппинги (без изменений) ...
    private ChatChannel mapStringToChannel(String name) {
        switch (name) {
            case "Торг": return ChatChannel.TRADE;
            case "Личное": return ChatChannel.WHISPER; // Переименовал
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
