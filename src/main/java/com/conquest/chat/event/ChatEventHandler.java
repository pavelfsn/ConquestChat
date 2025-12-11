package com.conquest.chat.event;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.client.ClientChatManager;
import com.conquest.chat.config.ServerConfig;
import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import com.conquest.chat.manager.AntiSpamManager;
import com.conquest.chat.manager.ServerChannelManager;
import com.conquest.chat.network.ClientChatMessagePacket;
import com.conquest.chat.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID)
public class ChatEventHandler {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");

    // --- SERVER SIDE ---
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) return;

        UUID playerId = player.getUUID();
        String message = event.getRawText();
        ChatChannel channel = ServerChannelManager.getChannel(playerId);

        // 1. Антиспам
        if (!AntiSpamManager.canSendMessage(player, channel, message)) {
            player.sendSystemMessage(Component.literal("Подождите перед отправкой сообщения!").withStyle(ChatFormatting.RED));
            event.setCanceled(true);
            return;
        }

        // 2. Обработка команд (шепот и т.д.)
        if (message.trim().startsWith("@")) {
            // handleWhisper(player, message); // Реализуй если нужно
            event.setCanceled(true);
            return;
        }

        // 3. Отмена ванильной рассылки
        event.setCanceled(true);

        // 4. Рассылка по каналам
        switch (channel) {
            case GLOBAL -> handleGlobalChat(player, message);
            case TRADE -> handleTradeChat(player, message);
            case COMBAT -> player.sendSystemMessage(Component.literal("Нельзя писать в канал Бой!").withStyle(ChatFormatting.RED));
            default -> handleLocalChat(player, message);
        }
    }

    // --- CLIENT SIDE ---
    @SubscribeEvent
    public static void onClientChatReceived(ClientChatReceivedEvent event) {
        // Попытка определить Action Bar (текст над хотбаром).
        // Если event.isOverlay() нет, можно попробовать проверить через reflection или просто игнорировать,
        // так как Action Bar обычно не сохраняется в истории чата.
        // В большинстве версий 1.19+ этот метод называется isOverlay() или isActionBar().
        // Если его нет - удаляем проверку, надеясь, что Forge не шлет Action Bar в этот ивент как обычный чат.

        // ВАЖНО: Если у тебя ошибка компиляции на isOverlay(), удали эту строку.
        // Action Bar (над хотбаром) обычно не ломает чат, просто дублируется.

        Component message = event.getMessage();
        String text = message.getString();

        // 1. Фильтруем боевые сообщения
        if (text.startsWith("[COMBAT]")) {
            ClientChatManager.getInstance().addMessage(ChatMessageType.COMBAT, message);
            event.setCanceled(true);
            return;
        }

        // 2. Ловим сообщения нашего мода (мы их сами отправляем через sendSystemMessage)
        // Чтобы не дублировать, можно добавить проверку, но проще просто добавить в историю
        ClientChatManager.getInstance().addMessage(ChatMessageType.GENERAL, message);

        // 3. Отменяем ванильное отображение
        // Это скроет сообщение из стандартного чата (который мы и так перекрыли, но это уберет "всплывание")
        event.setCanceled(true);
    }

    // --- CHAT LOGIC ---

    private static void handleGlobalChat(ServerPlayer player, String message) {
        Component component = formatMessage(player, message, ChatFormatting.GOLD);
        sendToAll(component, ChatMessageType.GENERAL);
    }

    private static void handleTradeChat(ServerPlayer player, String message) {
        Component component = formatMessage(player, message, ChatFormatting.GREEN);
        sendToAll(component, ChatMessageType.TRADE);
    }

    private static void handleLocalChat(ServerPlayer player, String message) {
        Component component = formatMessage(player, message, ChatFormatting.WHITE);
        double radius = ServerConfig.LOCAL_RADIUS.get();
        Vec3 pos = player.position();

        List<ServerPlayer> recipients = player.server.getPlayerList().getPlayers();
        for (ServerPlayer recipient : recipients) {
            if (recipient.level() == player.level() && recipient.position().distanceTo(pos) <= radius) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient),
                        new ClientChatMessagePacket(ChatMessageType.GENERAL, component));
            }
        }
    }

    private static Component formatMessage(ServerPlayer player, String message, ChatFormatting color) {
        String time = TIME_FORMAT.format(new Date());
        return Component.literal("[" + time + "] ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(player.getName().getString() + ": ")
                        .withStyle(color))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
    }

    private static void sendToAll(Component component, ChatMessageType type) {
        ClientChatMessagePacket packet = new ClientChatMessagePacket(type, component);
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
