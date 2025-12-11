package com.conquest.chat.manager;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChannelManager {

    private static final Map<UUID, ChatChannel> playerChannels = new HashMap<>();

    public static void setActiveChannel(UUID playerId, ChatChannel channel) {
        ChatChannel oldChannel = playerChannels.getOrDefault(playerId, ChatChannel.ALL);
        playerChannels.put(playerId, channel);
        ConquestChatMod.LOGGER.info("ChannelManager: Player {} switched from {} to {}",
                playerId, oldChannel, channel);
    }

    public static ChatChannel getActiveChannel(UUID playerId) {
        ChatChannel channel = playerChannels.getOrDefault(playerId, ChatChannel.ALL);
        ConquestChatMod.LOGGER.info("ChannelManager: Getting channel for player {}: {}",
                playerId, channel);
        return channel;
    }

    public static void removePlayer(UUID playerId) {
        ChatChannel removed = playerChannels.remove(playerId);
        ConquestChatMod.LOGGER.info("ChannelManager: Removed player {} (had channel {})",
                playerId, removed);
    }

    public static void clearAll() {
        playerChannels.clear();
        ConquestChatMod.LOGGER.info("ChannelManager: Cleared all player channels");
    }
}
