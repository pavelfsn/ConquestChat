package com.conquest.chat.network;

import com.conquest.chat.client.ClientChatManager;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientChatMessagePacket {

    private final ChatMessageType type;
    private final Component message;

    public ClientChatMessagePacket(ChatMessageType type, Component message) {
        this.type = type;
        this.message = message;
    }

    // Метод для записи пакета в буфер
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(type);
        buffer.writeComponent(message);
    }

    // Метод для чтения пакета из буфера (ДОЛЖЕН БЫТЬ STATIC)
    public static ClientChatMessagePacket decode(FriendlyByteBuf buffer) {
        return new ClientChatMessagePacket(
                buffer.readEnum(ChatMessageType.class),
                buffer.readComponent()
        );
    }

    // Метод для обработки пакета
    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Передаем сообщение в менеджер чата на клиенте
            ClientChatManager.getInstance().addMessage(type, message);
        });
        context.setPacketHandled(true);
    }
}
