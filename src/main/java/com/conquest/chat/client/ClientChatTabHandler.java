package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID, value = Dist.CLIENT)
public class ClientChatTabHandler {

    private static int tabCycleIndex = 0;
    private static List<String> matchingNames = new ArrayList<>();
    private static String lastPrefix = "";
    private static Field inputField = null;

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof ChatScreen chatScreen)) {
            return;
        }

        // Проверяем нажатие Tab
        if (event.getKeyCode() != GLFW.GLFW_KEY_TAB) {
            return;
        }

        // Отменяем стандартное поведение
        event.setCanceled(true);

        EditBox input = getChatInput(chatScreen);
        if (input == null) {
            ConquestChatMod.LOGGER.error("CLIENT: Failed to get chat input field");
            return;
        }

        String currentInput = input.getValue();
        ConquestChatMod.LOGGER.info("CLIENT: Tab pressed, current input: '{}'", currentInput);

        // Проверяем, начинается ли ввод с @
        if (!currentInput.startsWith("@")) {
            return;
        }

        // Извлекаем префикс после @
        int spaceIndex = currentInput.indexOf(' ');
        String prefix;

        if (spaceIndex > 0) {
            // Если есть пробел, больше не автодополняем
            return;
        } else {
            prefix = currentInput.substring(1); // Убираем @
        }

        // Если префикс изменился, сбрасываем список
        if (!prefix.equals(lastPrefix)) {
            tabCycleIndex = 0;
            matchingNames = getMatchingPlayerNames(prefix);
            lastPrefix = prefix;
            ConquestChatMod.LOGGER.info("CLIENT: Found {} matching names for prefix '{}'", matchingNames.size(), prefix);
        }

        if (matchingNames.isEmpty()) {
            return;
        }

        // Циклически перебираем подходящие ники
        String selectedName = matchingNames.get(tabCycleIndex);
        tabCycleIndex = (tabCycleIndex + 1) % matchingNames.size();

        // Обновляем поле ввода
        input.setValue("@" + selectedName + " ");
        ConquestChatMod.LOGGER.info("CLIENT: Autocompleted to '@{} '", selectedName);
    }

    private static EditBox getChatInput(ChatScreen chatScreen) {
        if (inputField == null) {
            try {
                // Ищем поле типа EditBox в ChatScreen
                for (Field field : ChatScreen.class.getDeclaredFields()) {
                    if (EditBox.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        inputField = field;
                        ConquestChatMod.LOGGER.info("CLIENT: Found chat input field: {}", field.getName());
                        break;
                    }
                }
            } catch (Exception e) {
                ConquestChatMod.LOGGER.error("CLIENT: Failed to find chat input field", e);
                return null;
            }
        }

        if (inputField != null) {
            try {
                return (EditBox) inputField.get(chatScreen);
            } catch (Exception e) {
                ConquestChatMod.LOGGER.error("CLIENT: Failed to get chat input field value", e);
            }
        }

        return null;
    }

    private static List<String> getMatchingPlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.getConnection() == null) {
            return names;
        }

        ClientPacketListener connection = minecraft.getConnection();
        Collection<PlayerInfo> players = connection.getOnlinePlayers();

        String lowerPrefix = prefix.toLowerCase();

        for (PlayerInfo playerInfo : players) {
            String playerName = playerInfo.getProfile().getName();

            // Пропускаем самого себя
            if (minecraft.player != null && playerName.equals(minecraft.player.getName().getString())) {
                continue;
            }

            // Фильтруем по префиксу
            if (lowerPrefix.isEmpty() || playerName.toLowerCase().startsWith(lowerPrefix)) {
                names.add(playerName);
            }
        }

        // Сортируем по алфавиту
        names.sort(String.CASE_INSENSITIVE_ORDER);

        return names;
    }
}
