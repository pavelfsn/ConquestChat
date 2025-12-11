package com.conquest.chat.client;

import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientChatManager {

    private static ClientChatManager instance;
    private final Map<ChatChannel, List<Component>> messages = new HashMap<>();
    private ChatChannel activeTab = ChatChannel.ALL;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

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
        // Форматирование времени: [HH:mm:ss]
        String timeStamp = "[" + LocalTime.now().format(TIME_FORMATTER) + "] ";
        Component formatted = Component.literal(timeStamp).append(originalMessage);

        // Фильтрация по каналам
        messages.computeIfAbsent(ChatChannel.ALL, k -> new ArrayList<>()).add(formatted);

        ChatChannel target = mapTypeToChannel(type);
        if (target != ChatChannel.ALL) {
            messages.computeIfAbsent(target, k -> new ArrayList<>()).add(formatted);
        }

        trimHistory(ChatChannel.ALL);
        trimHistory(target);
    }

    // Специальный метод для добавления логов боя с твоим форматом
    public void addCombatLog(boolean isOutgoing, String playerName, String entityName, float amount, boolean isKill) {
        String time = LocalTime.now().format(TIME_FORMATTER);
        String prefix = isOutgoing ? "[Урон. Исходящий]" : "[Урон. Входящий]";
        String action = isKill ? (isOutgoing ? "Вы убили" : "Вас убил") : (isOutgoing ? "Вы ранили" : "Вас ранил");

        String text;
        if (isKill) {
            text = String.format("[%s] %s [%s]: %s %s", time, prefix, playerName, action, entityName);
        } else {
            text = String.format("[%s] %s [%s]: %s %s (%.1f)", time, prefix, playerName, action, entityName, amount);
        }

        // Цвет: Красный для входящего, Зеленый/Желтый для исходящего (можно настроить стилями)
        Component comp = Component.literal(text).withStyle(isOutgoing ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED);

        addMessage(ChatMessageType.COMBAT, comp);
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

    private ChatChannel mapStringToChannel(String name) {
        switch (name) {
            case "Торг": return ChatChannel.TRADE;
            case "Личное": return ChatChannel.WHISPER;
            case "Урон": return ChatChannel.COMBAT;
            case "Общий":
            default: return ChatChannel.ALL;
        }
    }

    private ChatChannel mapTypeToChannel(ChatMessageType type) {
        if (type == ChatMessageType.TRADE) return ChatChannel.TRADE;
        if (type == ChatMessageType.WHISPER) return ChatChannel.WHISPER;
        if (type == ChatMessageType.COMBAT) return ChatChannel.COMBAT;

        // GENERAL и любые другие неизвестные типы шлем в ALL (Общий чат)
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
