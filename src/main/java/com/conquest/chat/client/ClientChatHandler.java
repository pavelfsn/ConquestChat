package com.conquest.chat.client;

import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class ClientChatHandler {

    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        /*
         * Ключевая правка:
         * - системные сообщения (ответы на команды, ошибки, контекст Brigadier и т.п.) -> ChatMessageType.SYSTEM
         * - обычный чат игроков -> ChatMessageType.GENERAL
         */

        Component message = event.getMessage();
        UUID sender = event.getSender(); // зарезервировано на будущее

        ChatMessageType type = event.isSystem() ? ChatMessageType.SYSTEM : ChatMessageType.GENERAL;
        ClientChatManager.getInstance().addMessage(type, message);

        // Отключаем ванильный чат, чтобы не было дублей
        event.setCanceled(true);
    }
}
