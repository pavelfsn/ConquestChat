package com.conquest.chat.channel;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;

public class ChannelHistory {

    private static final ChannelHistory INSTANCE = new ChannelHistory();

    private final Map<ChannelType, ChatChannel> channels;

    private ChannelHistory() {
        this.channels = new EnumMap<>(ChannelType.class);
        for (ChannelType type : ChannelType.values()) {
            this.channels.put(type, new ChatChannel(type));
        }
    }

    public static ChannelHistory getInstance() {
        return INSTANCE;
    }

    public ChatChannel getChannel(ChannelType type) {
        return channels.get(type);
    }

    // Старый API (строка)
    public void sendToChannel(ChannelType type, ServerPlayer sender, String message) {
        ChatChannel channel = getChannel(type);
        if (channel != null) {
            channel.addMessage(sender, message);
        }
    }

    // Новый API (готовый Component)
    public void sendToChannel(ChannelType type, ServerPlayer sender, Component message) {
        ChatChannel channel = getChannel(type);
        if (channel != null) {
            channel.addMessage(sender, message);
        }
    }

    public void sendSystemMessage(ChannelType type, String message) {
        ChatChannel channel = getChannel(type);
        if (channel != null) {
            channel.addSystemMessage(message);
        }
    }

    public void sendSystemMessage(ChannelType type, Component message) {
        ChatChannel channel = getChannel(type);
        if (channel != null) {
            channel.addSystemMessage(message);
        }
    }

    /**
     * ChatChannel#getMessages() возвращает копию списка, поэтому чистить нужно через пересоздание.
     */
    public void reset() {
        channels.clear();
        for (ChannelType type : ChannelType.values()) {
            channels.put(type, new ChatChannel(type));
        }
    }
}
