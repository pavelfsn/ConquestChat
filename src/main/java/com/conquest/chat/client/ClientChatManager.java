package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientChatManager {

    private static ClientChatManager instance;

    // Инициализируем сразу, чтобы не было null
    private final Map<ChatChannel, List<Component>> messages = new HashMap<>();
    private ChatChannel activeTab = ChatChannel.ALL;

    private ClientChatManager() {
        // Заполняем пустыми списками для всех каналов
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

    public void addMessage(ChatMessageType type, Component message) {
        messages.computeIfAbsent(ChatChannel.ALL, k -> new ArrayList<>()).add(message);

        ChatChannel target = mapTypeToChannel(type);
        if (target != ChatChannel.ALL) {
            messages.computeIfAbsent(target, k -> new ArrayList<>()).add(message);
        }
    }

    // Простой маппинг
    private ChatChannel mapTypeToChannel(ChatMessageType type) {
        if (type == ChatMessageType.TRADE) return ChatChannel.TRADE;
        if (type == ChatMessageType.WHISPER) return ChatChannel.WHISPER;
        // Проверка по имени для COMBAT, если он есть
        for (ChatChannel c : ChatChannel.values()) {
            if (c.name().equals(type.name())) return c;
        }
        return ChatChannel.ALL;
    }

    public List<Component> getMessagesForTab(ChatChannel tab) {
        ChatChannel target = (tab == null) ? activeTab : tab;
        return messages.getOrDefault(target, new ArrayList<>()); // Возвращаем пустой список, если null
    }

    public ChatChannel getActiveTab() { return activeTab; }
    public void setActiveTab(ChatChannel t) { this.activeTab = t; }

    public ChatMessageType getOutgoingType() {
        // Простой свитч для отправки
        if (activeTab == ChatChannel.TRADE) return ChatMessageType.TRADE;
        if (activeTab == ChatChannel.WHISPER) return ChatMessageType.WHISPER;
        return ChatMessageType.GENERAL;
    }

    public void clear() { messages.values().forEach(List::clear); }
}
