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
        // В 1.20.1 системные сообщения (включая overlay) помечаются как isSystem() = true
        // К сожалению, просто isSystem() = true может включать и важные серверные сообщения
        // Но обычно Overlay сообщения имеют sender = null и специфический контент

        // Более надежный способ для оверлея в 1.20.1 - это не перехватывать их здесь,
        // а надеяться, что ClientChatReceivedEvent вызывается только для чата.
        // Но если метод isOverlay() недоступен, попробуем проверить через isSystem() + косвенные признаки

        // Если это Action Bar (сообщение над хотбаром), оно обычно не должно попадать в чат.
        // В Forge 1.20.1 event.isSystem() возвращает true для сообщений от сервера (/say, /tellraw и т.д.)

        // ВАЖНО: В новых версиях есть event.getBoundChatType(), но он сложный.

        // Попробуем простое решение:
        // Если сообщение приходит, мы его обрабатываем. Если это спам в action bar,
        // он все равно вызовет ивент.

        // В 1.20.1 есть event.isSystem(). Если true - это сообщение от системы/сервера.
        // Обычно overlay сообщения (action bar) НЕ вызывают этот ивент в некоторых версиях Forge,
        // или вызывают с особым флагом.

        // Если у тебя нет доступа к event.isOverlay(), просто убери эту проверку пока.
        // Если оверлей начнет спамить в чат, добавим фильтр по содержимому.

        /*
        if (event.isOverlay()) {
            return;
        }
        */

        Component message = event.getMessage();
        UUID sender = event.getSender();

        ChatMessageType type = ChatMessageType.GENERAL;

        ClientChatManager.getInstance().addMessage(type, message);

        // Отменяем стандартную отрисовку, чтобы избежать дублирования
        event.setCanceled(true);
    }
}
