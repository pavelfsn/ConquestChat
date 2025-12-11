package com.conquest.chat.channel;

import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.Map;

public class ChannelManager {
    private static final ChannelManager INSTANCE = new ChannelManager();
    private final Map<ChannelType, ChatChannel> channels;

    private ChannelManager() {
        channels = new EnumMap<>(ChannelType.class);
        for (ChannelType type : ChannelType.values()) {
            channels.put(type, new ChatChannel(type));
        }
    }

    public static ChannelManager getInstance() {
        return INSTANCE;
    }

    public ChatChannel getChannel(ChannelType type) {
        return channels.get(type);
    }

    public void sendToChannel(ChannelType type, ServerPlayer sender, String message) {
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

    public void reset() {
        for (ChatChannel channel : channels.values()) {
            channel.getMessages().clear();
        }
    }
}
