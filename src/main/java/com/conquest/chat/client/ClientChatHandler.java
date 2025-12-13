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
        // Всё маршрутизируется Mixin'ом из ChatComponent#addMessage(...)
        // Здесь ничего не делаем и НЕ cancel'им событие, иначе часть сообщений может пропасть.
    }
}
