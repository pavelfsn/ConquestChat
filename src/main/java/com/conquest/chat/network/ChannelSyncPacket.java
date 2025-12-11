package com.conquest.chat.network;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.manager.ServerChannelManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChannelSyncPacket {

    private final ChatChannel channel;

    // Конструктор для создания пакета в коде
    public ChannelSyncPacket(ChatChannel channel) {
        this.channel = channel;
    }

    // Конструктор для чтения из буфера (используется внутри decode)
    public ChannelSyncPacket(FriendlyByteBuf buffer) {
        this.channel = buffer.readEnum(ChatChannel.class);
    }

    // --- ДОБАВЛЕННЫЙ МЕТОД ---
    // Статический метод декодирования, который нужен NetworkHandler'у
    public static ChannelSyncPacket decode(FriendlyByteBuf buffer) {
        return new ChannelSyncPacket(buffer);
    }
    // -------------------------

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.channel);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerChannelManager.setChannel(player.getUUID(), this.channel);
                ConquestChatMod.LOGGER.info("Player {} switched channel to {}", player.getName().getString(), this.channel);
            }
        });
        context.setPacketHandled(true);
    }
}
