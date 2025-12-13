package com.conquest.chat.event;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.channel.ChannelHistory;
import com.conquest.chat.channel.ChannelType;
import com.conquest.chat.channel.ChatChannel;
import com.conquest.chat.util.CooldownManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.conquest.chat.enums.ChatMessageType;
import com.conquest.chat.network.ClientChatMessagePacket;
import com.conquest.chat.network.NetworkHandler;
import net.minecraftforge.network.PacketDistributor;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final Map<UUID, UUID> LAST_WHISPER = new HashMap<>(); // Для будущей команды /r

    /**
     * Новый токен: [[item:id=xxxxx]]
     * Слот берём НЕ из текста, а из itemIdToSlot, присланной вместе с пакетом.
     */
    private static final Pattern ITEM_TOKEN = Pattern.compile("\\Q[[item:id=\\E([0-9a-zA-Z]+)\\Q]]\\E");

    public static void handleFromPacket(ServerPlayer sender, ChannelType originChannel, String rawMessage) {
        handleFromPacket(sender, originChannel, rawMessage, Map.of());
    }

    public static void handleFromPacket(ServerPlayer sender, ChannelType originChannel, String rawMessage, Map<String, Integer> itemIdToSlot) {
        if (sender == null || rawMessage == null) return;

        String message = rawMessage.trim();
        if (message.isEmpty()) return;

        // @Nick message -> личное сообщение
        if (message.startsWith("@")) {
            handleWhisperMessage(sender, message, originChannel, itemIdToSlot);
            return;
        }

        // /g, /t могут переопределять originChannel
        ChannelType override = parseChannel(message);
        ChannelType channel = (override != null) ? override : originChannel;

        if (override != null) {
            message = removeChannelPrefix(message);
            if (message.isEmpty()) return;
        }

        if (channel == ChannelType.WHISPER) {
            sender.sendSystemMessage(
                    Component.literal("❌ Для личных сообщений используйте формат: @Ник Сообщение")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (channel == ChannelType.COMBAT) {
            sender.sendSystemMessage(
                    Component.literal("❌ В боевой канал писать нельзя.")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (!CooldownManager.getInstance().canSendMessage(sender.getUUID(), channel)) {
            int remaining = CooldownManager.getInstance().getRemainingCooldown(sender.getUUID(), channel);
            sender.sendSystemMessage(
                    Component.literal("Подождите ещё " + remaining + " сек. перед отправкой в " + channel.getDisplayName())
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        Component richContent = buildMessageWithItemHovers(sender, message, itemIdToSlot);

        ChannelHistory history = ChannelHistory.getInstance();
        history.sendToChannel(channel, sender, richContent);

        if (channel.needsCooldown()) {
            CooldownManager.getInstance().setLastMessageTime(sender.getUUID(), channel);
        }

        ChatChannel historyChannel = history.getChannel(channel);
        if (historyChannel == null) return;

        List<ChatChannel.ChatMessage> msgs = historyChannel.getMessages();
        if (msgs.isEmpty()) return;

        Component out = msgs.get(msgs.size() - 1).toComponent();
        sendToChannel(sender, out, channel);
    }

    private static Component buildMessageWithItemHovers(ServerPlayer sender, String text, Map<String, Integer> itemIdToSlot) {
        Matcher m = ITEM_TOKEN.matcher(text);
        int last = 0;

        MutableComponent out = Component.empty();

        while (m.find()) {
            String before = text.substring(last, m.start());
            if (!before.isEmpty()) {
                out.append(Component.literal(before).withStyle(ChatFormatting.WHITE));
            }

            String id = m.group(1);
            Integer slotObj = (itemIdToSlot == null) ? null : itemIdToSlot.get(id);

            // Ключевая защита: если id не подтверждено картой из пакета — не превращаем в hover.
            if (slotObj == null) {
                out.append(Component.literal(m.group(0)).withStyle(ChatFormatting.WHITE));
                last = m.end();
                continue;
            }

            int slot = slotObj;
            ItemStack st = ItemStack.EMPTY;

            if (slot >= 0 && sender.getInventory() != null && slot < sender.getInventory().items.size()) {
                st = sender.getInventory().items.get(slot);
            }

            if (st == null || st.isEmpty()) {
                out.append(Component.literal("[пусто]").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                MutableComponent shown = Component.literal("[")
                        .append(st.getHoverName())
                        .append(Component.literal("]"));

                Style style = Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_ITEM,
                                new HoverEvent.ItemStackInfo(st.copy())
                        ));

                out.append(shown.setStyle(style));
            }

            last = m.end();
        }

        if (last < text.length()) {
            out.append(Component.literal(text.substring(last)).withStyle(ChatFormatting.WHITE));
        }

        return out;
    }

    private static void handleWhisperMessage(ServerPlayer sender, String message, ChannelType originChannel, Map<String, Integer> itemIdToSlot) {
        String[] parts = message.substring(1).split(" ", 2);
        if (parts.length < 2) {
            sender.sendSystemMessage(
                    Component.literal("❌ Неверный формат! Используйте: @ИмяИгрока Сообщение")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        String targetName = parts[0];
        String whisperMessage = parts[1];

        ServerPlayer targetPlayer = findPlayerByName(sender.server, targetName);
        if (targetPlayer == null) {
            sender.sendSystemMessage(
                    Component.literal("❌ Игрок " + targetName + " не найден!")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        if (targetPlayer.equals(sender)) {
            sender.sendSystemMessage(
                    Component.literal("❌ Нельзя отправить личное сообщение самому себе!")
                            .withStyle(ChatFormatting.RED)
            );
            return;
        }

        LAST_WHISPER.put(sender.getUUID(), targetPlayer.getUUID());
        LAST_WHISPER.put(targetPlayer.getUUID(), sender.getUUID());

        String timestamp = TIME_FORMAT.format(new Date());

        Component richWhisper = buildMessageWithItemHovers(sender, whisperMessage, itemIdToSlot);

        MutableComponent senderMessage = Component.literal("[" + timestamp + "] ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("[ЛС] ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal("Вы → ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(targetPlayer.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                .append(richWhisper);

        MutableComponent receiverMessage = Component.literal("[" + timestamp + "] ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("[ЛС] ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(sender.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" → Вам: ").withStyle(ChatFormatting.WHITE))
                .append(richWhisper);

        sender.sendSystemMessage(senderMessage);
        targetPlayer.sendSystemMessage(receiverMessage);

        ConquestChatMod.LOGGER.info("Whisper from " + sender.getName().getString() + " to " + targetPlayer.getName().getString() + ": " + whisperMessage);
    }

    private static ServerPlayer findPlayerByName(net.minecraft.server.MinecraftServer server, String name) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (player.getName().getString().equalsIgnoreCase(name)) return player;
        }
        return null;
    }

    private static ChannelType parseChannel(String message) {
        if (message.startsWith("/g ")) return ChannelType.GLOBAL;
        if (message.startsWith("/t ")) return ChannelType.TRADE;
        return null;
    }

    private static String removeChannelPrefix(String message) {
        if (message.startsWith("/g ")) return message.substring(3).trim();
        if (message.startsWith("/t ")) return message.substring(3).trim();
        return message;
    }

    private static void sendToChannel(ServerPlayer sender, Component message, ChannelType channel) {
        ChatMessageType type = toClientType(channel);

        sender.server.getPlayerList().getPlayers().forEach(player -> {
            if (canReceiveMessage(player, sender, channel)) {
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new ClientChatMessagePacket(type, message)
                );
            }
        });
    }

    private static ChatMessageType toClientType(ChannelType channel) {
        return switch (channel) {
            case TRADE -> ChatMessageType.TRADE;
            case WHISPER -> ChatMessageType.PRIVATE;
            case COMBAT -> ChatMessageType.COMBAT;
            default -> ChatMessageType.GENERAL; // ALL/GLOBAL/и т.п.
        };
    }

    private static boolean canReceiveMessage(ServerPlayer receiver, ServerPlayer sender, ChannelType channel) {
        switch (channel) {
            case GLOBAL:
                return receiver.level().dimension().equals(sender.level().dimension());
            case ALL:
            case TRADE:
                return receiver.level().dimension().equals(sender.level().dimension())
                        && receiver.distanceToSqr(sender) < 10000;
            case COMBAT:
            case WHISPER:
            default:
                return false;
        }
    }
}
