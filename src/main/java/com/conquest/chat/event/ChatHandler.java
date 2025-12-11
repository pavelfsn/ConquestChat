package com.conquest.chat.event;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.channel.ChannelType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatHandler {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final Map<UUID, UUID> LAST_WHISPER = new HashMap<>(); // Для будущей команды /r

    private static void handleWhisperMessage(ServerPlayer sender, String message, ChannelType originChannel) {
        // Парсим ник получателя
        String[] parts = message.substring(1).split(" ", 2);
        if (parts.length < 2) {
            sender.sendSystemMessage(
                    Component.literal("❌ Неверный формат! Используйте: @ИмяИгрока Сообщение")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        String targetName = parts[0];
        String whisperMessage = parts[1];

        // Ищем игрока на сервере
        ServerPlayer targetPlayer = findPlayerByName(sender.server, targetName);

        if (targetPlayer == null) {
            sender.sendSystemMessage(
                    Component.literal("❌ Игрок " + targetName + " не найден!")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        // Нельзя отправлять личное сообщение самому себе
        if (targetPlayer.equals(sender)) {
            sender.sendSystemMessage(
                    Component.literal("❌ Нельзя отправить личное сообщение самому себе!")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        // Сохраняем последнего собеседника для команды /r (будущее)
        LAST_WHISPER.put(sender.getUUID(), targetPlayer.getUUID());
        LAST_WHISPER.put(targetPlayer.getUUID(), sender.getUUID());

        String timestamp = TIME_FORMAT.format(new Date());

        // Форматируем сообщение для отправителя
        MutableComponent senderMessage = Component.literal("[" + timestamp + "] ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("[ЛС] ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("Вы → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(targetPlayer.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(": " + whisperMessage).withStyle(ChatFormatting.WHITE));

        // Форматируем сообщение для получателя
        MutableComponent receiverMessage = Component.literal("[" + timestamp + "] ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("[ЛС] ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(sender.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" → Вам: " + whisperMessage).withStyle(ChatFormatting.WHITE));

        // Отправляем сообщения
        sender.sendSystemMessage(senderMessage);
        targetPlayer.sendSystemMessage(receiverMessage);

        ConquestChatMod.LOGGER.info("Whisper from " + sender.getName().getString() + " to " + targetPlayer.getName().getString() + ": " + whisperMessage);
    }

    private static ServerPlayer findPlayerByName(net.minecraft.server.MinecraftServer server, String name) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (player.getName().getString().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private static ChannelType parseChannel(String message) {
        // Новые префиксы: /g - Глобал, /t - Торг, @Nick - Личное
        if (message.startsWith("/g ")) return ChannelType.GLOBAL;
        if (message.startsWith("/t ")) return ChannelType.TRADE;
        // @Nick обрабатывается отдельно
        return null; // Нет префикса
    }

    private static String removeChannelPrefix(String message) {
        if (message.startsWith("/g ")) {
            return message.substring(3).trim();
        }
        if (message.startsWith("/t ")) {
            return message.substring(3).trim();
        }
        return message;
    }

    private static MutableComponent formatMessage(ServerPlayer player, String message, ChannelType channel) {
        String timestamp = TIME_FORMAT.format(new Date());
        String channelName = getChannelDisplayName(channel);

        // Формат: [Время] [Чат] [NickName]: Сообщение
        return Component.literal("[" + timestamp + "] ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("[" + channelName + "] ").withStyle(channel.getColor()))
                .append(Component.literal("[" + player.getName().getString() + "]").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": " + message).withStyle(ChatFormatting.GRAY));
    }

    private static String getChannelDisplayName(ChannelType channel) {
        return switch (channel) {
            case GLOBAL -> "Глоб";
            case TRADE -> "Торг";
            case WHISPER -> "ЛС";
            case COMBAT -> "Бой";
            case ALL -> "Общий";
        };
    }

    private static void sendToChannel(ServerPlayer sender, MutableComponent message, ChannelType channel) {
        sender.server.getPlayerList().getPlayers().forEach(player -> {
            if (canReceiveMessage(player, sender, channel)) {
                player.sendSystemMessage(message);
            }
        });
    }

    private static boolean canReceiveMessage(ServerPlayer receiver, ServerPlayer sender, ChannelType channel) {
        switch (channel) {
            case GLOBAL:
                // Только игроки в том же мире (dimension)
                return receiver.level().dimension().equals(sender.level().dimension());
            case ALL:
            case TRADE:
                // Радиус 100 блоков + тот же мир
                return receiver.level().dimension().equals(sender.level().dimension())
                        && receiver.distanceToSqr(sender) < 10000;
            case COMBAT:
                // COMBAT - только логи, игроки не пишут
                return false;
            case WHISPER:
                // WHISPER обрабатывается через @NickName
                return false;
            default:
                return false;
        }
    }
}
