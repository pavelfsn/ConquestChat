package com.conquest.chat.network;

import com.conquest.chat.ConquestChatMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ConquestChatMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;

        // ClientChatMessagePacket (Сервер -> Клиент)
        CHANNEL.registerMessage(id++,
                ClientChatMessagePacket.class,
                ClientChatMessagePacket::encode,
                ClientChatMessagePacket::decode,
                ClientChatMessagePacket::handle);

        // ChannelSyncPacket (Клиент -> Сервер)
        CHANNEL.registerMessage(id++,
                ChannelSyncPacket.class,
                ChannelSyncPacket::encode,
                ChannelSyncPacket::decode,
                ChannelSyncPacket::handle);
    }
}
