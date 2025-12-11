package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {

    // Объявляем переменную как public static, чтобы ClientEventHandler ее видел
    public static final KeyMapping SWITCH_CHANNEL = new KeyMapping(
            "key.conquestchat.switch_channel", // Ключ перевода
            KeyConflictContext.IN_GAME,        // Контекст (работает в игре)
            InputConstants.Type.KEYSYM,        // Тип (клавиатура)
            GLFW.GLFW_KEY_TAB,                 // Кнопка по умолчанию (TAB)
            "key.category.conquestchat"        // Категория в настройках
    );

    // Метод регистрации (вызывается из ClientEventHandler)
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(SWITCH_CHANNEL);
    }
}
