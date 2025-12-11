package com.conquest.chat.manager;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.config.ServerConfig;
import com.conquest.chat.enums.ChatChannel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiSpamManager {

    private static final Map<UUID, Map<ChatChannel, Long>> lastMessageTime = new HashMap<>();
    private static final Map<UUID, Map<ChatChannel, String>> lastMessageContent = new HashMap<>();

    public static boolean canSendMessage(ServerPlayer player, ChatChannel channel, String message) {
        UUID playerId = player.getUUID();
        long currentTime = System.currentTimeMillis();

        // Инициализация для нового игрока
        lastMessageTime.putIfAbsent(playerId, new HashMap<>());
        lastMessageContent.putIfAbsent(playerId, new HashMap<>());

        Map<ChatChannel, Long> playerTimes = lastMessageTime.get(playerId);
        Map<ChatChannel, String> playerMessages = lastMessageContent.get(playerId);

        // Проверка дублирования
        String lastMsg = playerMessages.get(channel);
        if (message.equals(lastMsg)) {
            player.sendSystemMessage(Component.literal("§c[Анти-спам] §fНельзя отправлять одинаковые сообщения подряд!"));
            ConquestChatMod.LOGGER.warn("Player {} tried to send duplicate message in channel {}",
                    player.getName().getString(), channel);
            return false;
        }

        // Проверка кулдауна
        long cooldown = getCooldown(channel);
        Long lastTime = playerTimes.get(channel);

        if (lastTime != null) {
            long timeSince = currentTime - lastTime;
            if (timeSince < cooldown * 1000) {
                long remaining = (cooldown * 1000 - timeSince) / 1000;
                player.sendSystemMessage(Component.literal("§c[Анти-спам] §fПодождите еще " + remaining + " сек."));
                ConquestChatMod.LOGGER.warn("Player {} is on cooldown for channel {} ({} sec remaining)",
                        player.getName().getString(), channel, remaining);
                return false;
            }
        }

        // Сохраняем данные
        playerTimes.put(channel, currentTime);
        playerMessages.put(channel, message);

        return true;
    }

    private static long getCooldown(ChatChannel channel) {
        return switch (channel) {
            case GLOBAL -> ServerConfig.GLOBAL_COOLDOWN.get();
            case TRADE -> ServerConfig.TRADE_COOLDOWN.get();
            case ALL, COMBAT -> ServerConfig.LOCAL_COOLDOWN.get();
            case WHISPER -> 1L; // 1 секунда для личных сообщений
        };
    }

    public static void clearPlayerData(UUID playerId) {
        lastMessageTime.remove(playerId);
        lastMessageContent.remove(playerId);
        ConquestChatMod.LOGGER.info("AntiSpamManager: Cleared data for player {}", playerId);
    }
}
