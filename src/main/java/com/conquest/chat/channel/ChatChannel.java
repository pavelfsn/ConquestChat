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

    public void addMessage(ServerPlayer sender, String message) {
        ChatMessage chatMessage = new ChatMessage(
                sender.getUUID(),
                sender.getName().getString(),
                message,
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
        ChatMessage chatMessage = new ChatMessage(
                null,
                "СИСТЕМА",
                message,
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
        private final String message;
        private final ChannelType channel;
        private final long timestamp;

        public ChatMessage(UUID senderId, String senderName, String message, ChannelType channel, long timestamp) {
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

            // Само сообщение
            component.append(Component.literal(message).withStyle(ChatFormatting.WHITE));

            return component;
        }

        public UUID getSenderId() {
            return senderId;
        }

        public String getSenderName() {
            return senderName;
        }

        public String getMessage() {
            return message;
        }

        public ChannelType getChannel() {
            return channel;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
