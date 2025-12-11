package com.conquest.chat.client;

import com.conquest.chat.enums.ChatChannel;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientChatManager {

    private static ClientChatManager instance;
    private final Map<ChatChannel, List<Component>> messages = new HashMap<>();
    private ChatChannel activeTab = ChatChannel.ALL;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Pattern DAMAGE_DEALT_PATTERN = Pattern.compile("Вы нанесли ([0-9.]+) урона по (.+)");
    private static final Pattern KILL_PATTERN = Pattern.compile("Вы убили (.+)");
    private static final Pattern EXISTING_TIME_PATTERN = Pattern.compile("^\\[\\d{2}:\\d{2}:\\d{2}\\]");

    private ClientChatManager() {
        for (ChatChannel channel : ChatChannel.values()) {
            messages.put(channel, new ArrayList<>());
        }
    }

    public static ClientChatManager getInstance() {
        if (instance == null) {
            instance = new ClientChatManager();
        }
        return instance;
    }

    public void addMessage(ChatMessageType type, Component originalMessage) {
        String cleanText = originalMessage.getString().trim();

        ChatChannel detectedChannel = ChatChannel.ALL;
        ChatFormatting color = ChatFormatting.WHITE;
        String prefix = "";

        // 1. ОПРЕДЕЛЯЕМ КАНАЛ
        if (type == ChatMessageType.COMBAT || cleanText.startsWith("[COMBAT]") || DAMAGE_DEALT_PATTERN.matcher(cleanText).find()) {
            detectedChannel = ChatChannel.COMBAT;
        } else if (cleanText.contains("[Торг]") || type == ChatMessageType.TRADE) {
            detectedChannel = ChatChannel.TRADE;
            color = ChatFormatting.BLUE; // &9
            if (!cleanText.contains("[Торг]")) prefix = "[Торг] ";
        } else if (cleanText.contains("шепчет") || cleanText.contains("прошептали") || type == ChatMessageType.WHISPER) {
            detectedChannel = ChatChannel.WHISPER;
            color = ChatFormatting.DARK_PURPLE; // &5
        } else {
            detectedChannel = ChatChannel.ALL;
            color = ChatFormatting.WHITE;
            if (!cleanText.startsWith("[Общий]")) prefix = "[Общий] ";
        }

        Component finalMessage;

        if (detectedChannel == ChatChannel.COMBAT) {
            finalMessage = reformatCombatMessage(originalMessage);
        } else {
            MutableComponent messageBuilder = Component.empty();

            // 2. ВРЕМЯ (Сначала время, серым цветом)
            // [HH:mm:ss]
            if (!EXISTING_TIME_PATTERN.matcher(cleanText).find()) {
                String timeStr = "[" + LocalTime.now().format(TIME_FORMATTER) + "] ";
                // Важно: время всегда серое, независимо от канала
                messageBuilder.append(Component.literal(timeStr).withStyle(ChatFormatting.GRAY));
            }

            // 3. ПРЕФИКС (Потом префикс, цветом канала)
            // [Общий] / [Торг]
            final ChatFormatting finalColor = color;
            if (!prefix.isEmpty()) {
                messageBuilder.append(Component.literal(prefix).withStyle(finalColor));
            }

            // 4. ТЕКСТ (Потом текст, цветом канала)
            // Никнейм: msg
            MutableComponent contentComp = originalMessage.copy();

            if (detectedChannel != ChatChannel.ALL) {
                // Если это спец. канал, мы хотим покрасить ВСЁ сообщение в цвет канала.
                // Чтобы избежать сброса цвета (белый текст после синего префикса),
                // мы применяем стиль ко всему добавленному контенту.

                // Вариант А: Обернуть контент в компонент с нужным стилем
                contentComp = Component.empty().append(contentComp).withStyle(finalColor);
            }

            messageBuilder.append(contentComp);
            finalMessage = messageBuilder;
        }

        // 5. СОХРАНЯЕМ И ДУБЛИРУЕМ

        // Добавляем в "Родной" канал
        messages.computeIfAbsent(detectedChannel, k -> new ArrayList<>()).add(finalMessage);

        // Дублируем в [Общий], если это [Торг] или [Личное] (или любой другой не общий)
        if (detectedChannel != ChatChannel.ALL) {
            messages.computeIfAbsent(ChatChannel.ALL, k -> new ArrayList<>()).add(finalMessage);
        }

        // Доп. требование: Дублировать [Личное] в [Торг]?
        // В ТЗ было: "добавь дублирование текста во вкладку [Торг]".
        // Если ты имел в виду, что сообщения из Общего должны попадать в Торг - это странно.
        // Если ты имел в виду, что сообщения из Торг должны попадать в Общий - это уже сделано выше.
        // Если ты имел в виду, что сообщения, отправленные В ТОРГ, должны отображаться В ТОРГЕ - это первая строка (add native).

        // На всякий случай, если ты хочешь видеть ВСЕ сообщения во вкладке Торг (как в Общем):
        // (Обычно так не делают, но если надо - раскомментируй)
        // if (detectedChannel == ChatChannel.ALL) {
        //     messages.computeIfAbsent(ChatChannel.TRADE, k -> new ArrayList<>()).add(finalMessage);
        // }

        trimHistory(ChatChannel.ALL);
        trimHistory(detectedChannel);
        // Если добавляли дубликаты, триммим и их
        if (detectedChannel != ChatChannel.ALL) trimHistory(ChatChannel.ALL);
    }

    // ... reformatCombatMessage и прочие методы без изменений ...

    private Component reformatCombatMessage(Component msg) {
        String text = msg.getString().replace("[COMBAT] ", "").trim();
        String time = LocalTime.now().format(TIME_FORMATTER);
        Matcher mDealt = DAMAGE_DEALT_PATTERN.matcher(text);
        if (mDealt.find()) {
            return Component.literal(String.format("[%s] [Урон. Исходящий]: Вы ранили %s (%s)", time, mDealt.group(2), mDealt.group(1))).withStyle(ChatFormatting.RED);
        }
        Matcher mKill = KILL_PATTERN.matcher(text);
        if (mKill.find()) {
            return Component.literal(String.format("[%s] [Урон. Исходящий]: Вы убили %s", time, mKill.group(1))).withStyle(ChatFormatting.GOLD);
        }
        return Component.literal("[" + time + "] " + text).withStyle(ChatFormatting.RED);
    }

    private void trimHistory(ChatChannel channel) {
        List<Component> list = messages.get(channel);
        if (list != null && list.size() > 100) list.remove(0);
    }

    public List<Component> getMessages(String channelName) {
        return getMessagesForTab(mapStringToChannel(channelName));
    }

    public List<Component> getMessagesForTab(ChatChannel tab) {
        ChatChannel target = (tab == null) ? activeTab : tab;
        return messages.getOrDefault(target, new ArrayList<>());
    }

    private ChatChannel mapStringToChannel(String name) {
        switch (name) {
            case "Торг": return ChatChannel.TRADE;
            case "Личное": return ChatChannel.WHISPER;
            case "Урон": return ChatChannel.COMBAT;
            case "Общий": default: return ChatChannel.ALL;
        }
    }

    public void setActiveTabName(String name) { this.activeTab = mapStringToChannel(name); }
    public String getActiveTabName() {
        switch (activeTab) {
            case TRADE: return "Торг";
            case WHISPER: return "Личное";
            case COMBAT: return "Урон";
            default: return "Общий";
        }
    }
    public void clear() { messages.values().forEach(List::clear); }
}
