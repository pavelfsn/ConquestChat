package com.conquest.chat.network;

import com.conquest.chat.channel.ChannelManager;
import com.conquest.chat.channel.ChannelType;
import com.conquest.chat.util.CooldownManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChatMessagePacket {
    private final String message;
    private final int channelId;

    public ChatMessagePacket(String message, ChannelType channel) {
        this.message = message;
        this.channelId = channel.ordinal();
    }

    public ChatMessagePacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf();
        this.channelId = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(message);
        buf.writeInt(channelId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ChannelType channel = ChannelType.values()[channelId];

            // Проверка кулдауна
            if (!CooldownManager.getInstance().canSendMessage(player.getUUID(), channel)) {
                int remaining = CooldownManager.getInstance().getRemainingCooldown(player.getUUID(), channel);
                player.sendSystemMessage(
                        Component.literal("Подождите ещё " + remaining + " сек. перед отправкой в " + channel.getDisplayName())
                                .withStyle(ChatFormatting.RED)
                );
                return;
            }

            // Сохраняем в канале
            ChannelManager.getInstance().sendToChannel(channel, player, message);

            // Устанавливаем кулдаун
            if (channel.needsCooldown()) {
                CooldownManager.getInstance().setLastMessageTime(player.getUUID(), channel);
            }

            // Отправляем всем игрокам
            Component chatMessage = ChannelManager.getInstance()
                    .getChannel(channel)
                    .getMessages()
                    .get(ChannelManager.getInstance().getChannel(channel).getMessages().size() - 1)
                    .toComponent();

            player.getServer().getPlayerList().getPlayers().forEach(p ->
                    p.sendSystemMessage(chatMessage)
            );
        });

        return true;
    }
}
