package com.conquest.chat.network;

import com.conquest.chat.channel.ChannelType;
import com.conquest.chat.event.ChatHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ChatMessagePacket {

    private final String message;
    private final int channelId;

    /**
     * Защита от ручного токена:
     * сервер будет показывать hover только для id, присутствующих в этой карте.
     */
    private final Map<String, Integer> itemIdToSlot;

    public ChatMessagePacket(String message, ChannelType channel, Map<String, Integer> itemIdToSlot) {
        this.message = message;
        this.channelId = channel.ordinal();
        this.itemIdToSlot = (itemIdToSlot == null) ? Map.of() : Map.copyOf(itemIdToSlot);
    }

    public ChatMessagePacket(String message, ChannelType channel) {
        this(message, channel, Map.of());
    }

    public ChatMessagePacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf();
        this.channelId = buf.readInt();

        int count = buf.readVarInt();
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            int slot = buf.readVarInt();
            map.put(id, slot);
        }
        this.itemIdToSlot = map;
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(message);
        buf.writeInt(channelId);

        buf.writeVarInt(itemIdToSlot.size());
        for (Map.Entry<String, Integer> e : itemIdToSlot.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ChannelType[] values = ChannelType.values();
            if (channelId < 0 || channelId >= values.length) return;

            ChannelType channel = values[channelId];
            ChatHandler.handleFromPacket(player, channel, message, itemIdToSlot);
        });

        context.setPacketHandled(true);
        return true;
    }
}
