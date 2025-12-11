package com.conquest.chat.util;

import com.conquest.chat.channel.ChannelType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {
    private static final CooldownManager INSTANCE = new CooldownManager();
    private final Map<UUID, Map<ChannelType, Long>> cooldowns;

    private CooldownManager() {
        this.cooldowns = new HashMap<>();
    }

    public static CooldownManager getInstance() {
        return INSTANCE;
    }

    public boolean canSendMessage(UUID playerId, ChannelType channel) {
        if (!channel.needsCooldown()) {
            return true;
        }

        Map<ChannelType, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return true;
        }

        Long lastMessageTime = playerCooldowns.get(channel);
        if (lastMessageTime == null) {
            return true;
        }

        long currentTime = System.currentTimeMillis();
        long timePassed = (currentTime - lastMessageTime) / 1000; // в секундах

        return timePassed >= channel.getCooldownSeconds();
    }

    public int getRemainingCooldown(UUID playerId, ChannelType channel) {
        if (!channel.needsCooldown()) {
            return 0;
        }

        Map<ChannelType, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return 0;
        }

        Long lastMessageTime = playerCooldowns.get(channel);
        if (lastMessageTime == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long timePassed = (currentTime - lastMessageTime) / 1000;
        int remaining = (int) (channel.getCooldownSeconds() - timePassed);

        return Math.max(0, remaining);
    }

    public void setLastMessageTime(UUID playerId, ChannelType channel) {
        cooldowns.computeIfAbsent(playerId, k -> new HashMap<>())
                .put(channel, System.currentTimeMillis());
    }

    public void clearCooldown(UUID playerId, ChannelType channel) {
        Map<ChannelType, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns != null) {
            playerCooldowns.remove(channel);
        }
    }

    public void clearAllCooldowns(UUID playerId) {
        cooldowns.remove(playerId);
    }
}
