package com.conquest.chat.manager;

import com.conquest.chat.enums.ChatChannel;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerChannelManager {

    // Хранит активный канал для каждого игрока
    private static final Map<UUID, ChatChannel> playerChannels = new HashMap<>();

    public static void setChannel(UUID playerId, ChatChannel channel) {
        playerChannels.put(playerId, channel);
    }

    public static ChatChannel getChannel(UUID playerId) {
        return playerChannels.getOrDefault(playerId, ChatChannel.ALL); // По умолчанию ALL
    }

    // Метод, которого не хватало для PlayerEventHandler
    public static void removePlayer(ServerPlayer player) {
        if (player != null) {
            playerChannels.remove(player.getUUID());
        }
    }
}
