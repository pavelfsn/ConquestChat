package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import com.conquest.chat.network.ChannelSyncPacket;
import com.conquest.chat.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ClientChatHandler {

    private static ChatChannel currentChannel = ChatChannel.ALL;

    public static void switchChannel() {
        ConquestChatMod.LOGGER.info("CLIENT: switchChannel() called, current channel: {}", currentChannel);

        ChatChannel oldChannel = currentChannel;

        // Добавлен COMBAT и обработка GLOBAL
        currentChannel = switch (currentChannel) {
            case ALL -> ChatChannel.GLOBAL;
            case GLOBAL -> ChatChannel.TRADE;
            case TRADE -> ChatChannel.WHISPER;
            case WHISPER -> ChatChannel.COMBAT;
            case COMBAT -> ChatChannel.ALL;
        };

        ConquestChatMod.LOGGER.info("CLIENT: Switched from {} to {}", oldChannel, currentChannel);

        try {
            NetworkHandler.CHANNEL.sendToServer(new ChannelSyncPacket(currentChannel));
            ConquestChatMod.LOGGER.info("CLIENT: Packet sent to server");
        } catch (Exception e) {
            ConquestChatMod.LOGGER.error("CLIENT: Failed to send packet", e);
        }

        // Сообщение-подсказка по текущему каналу
        String message = switch (currentChannel) {
            case ALL -> "§7[Чат] §fВы переключились на §eОбщий чат §7(100 блоков)";
            case GLOBAL -> "§7[Чат] §fВы переключились на §6Глобальный чат §7(весь сервер)";
            case TRADE -> "§7[Чат] §fВы переключились на §aТорговый чат §7(радиус из конфига)";
            case WHISPER -> "§7[Чат] §fЛичные сообщения: §e@НикИгрока сообщение §7(Tab для автодополнения)";
            case COMBAT -> "§7[Чат] §fВы переключились на §cБоевой канал §7(боевые логи)";
        };

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
            ConquestChatMod.LOGGER.info("CLIENT: Notification shown to player");
        }

        // Обновляем активную вкладку клиентского менеджера
        ClientChatManager.getInstance().setActiveTab(currentChannel);
    }

    public static ChatChannel getCurrentChannel() {
        return currentChannel;
    }

    public static ChatMessageType getCurrentMessageType() {
        return ClientChatManager.getInstance().getOutgoingType();
    }
}
