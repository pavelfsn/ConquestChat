package com.conquest.chat.channel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatChannel {

    private final ChannelType type;
    private final List<ChatMessage> messages;

    private static final int MAX_MESSAGES = 100;

    public ChatChannel(ChannelType type) {
        this.type = type;
        this.messages = new ArrayList<>();
    }

    // Старый API оставляем для совместимости
    public void addMessage(ServerPlayer sender, String message) {
        addMessage(sender, Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    // Новый API: храним уже готовый Component (в т.ч. с hover на предметы)
    public void addMessage(ServerPlayer sender, Component messageComponent) {
        ChatMessage chatMessage = new ChatMessage(
                sender.getUUID(),
                sender.getName().getString(),
                messageComponent,
                type,
                System.currentTimeMillis()
        );

        messages.add(chatMessage);

        // Ограничение истории
        if (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public void addSystemMessage(String message) {
        addSystemMessage(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    public void addSystemMessage(Component messageComponent) {
        ChatMessage chatMessage = new ChatMessage(
                null,
                "СИСТЕМА",
                messageComponent,
                type,
                System.currentTimeMillis()
        );

        messages.add(chatMessage);

        if (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public ChannelType getType() {
        return type;
    }

    public static class ChatMessage {

        private final UUID senderId;
        private final String senderName;
        private final Component message;
        private final ChannelType channel;
        private final long timestamp;

        public ChatMessage(UUID senderId, String senderName, Component message, ChannelType channel, long timestamp) {
            this.senderId = senderId;
            this.senderName = senderName;
            this.message = message;
            this.channel = channel;
            this.timestamp = timestamp;
        }

        public MutableComponent toComponent() {
            MutableComponent component = Component.empty();

            // Префикс канала
            component.append(channel.getPrefix());

            // Имя отправителя (если не система)
            if (senderId != null) {
                component.append(Component.literal(senderName + ": ").withStyle(channel.getColor()));
            }

            // Само сообщение (уже может содержать hover на предметы)
            component.append(message);

            return component;
        }

        public UUID getSenderId() {
            return senderId;
        }

        public String getSenderName() {
            return senderName;
        }

        public Component getMessageComponent() {
            return message;
        }

        // Для совместимости/логов
        public String getMessage() {
            return message.getString();
        }

        public ChannelType getChannel() {
            return channel;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
