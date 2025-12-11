package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        if (KeyBindings.SWITCH_CHANNEL != null) {
            event.register(KeyBindings.SWITCH_CHANNEL);
        }
    }

    // Вложенный класс для событий Forge (тика и открытия экрана)
    @Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onOpenScreen(ScreenEvent.Opening event) {
            // Проверяем, что открывается именно ChatScreen (ванильный), а не наш и не какой-то другой
            if (event.getNewScreen() instanceof ChatScreen && !(event.getNewScreen() instanceof CustomChatScreen)) {

                // Логируем попытку подмены
                ConquestChatMod.LOGGER.info("[ClientEventHandler] Intercepted ChatScreen opening. Replacing with CustomChatScreen.");

                String defaultText = "";
                // Пытаемся безопасно получить текст
                try {
                    // defaultText = ((ChatScreen) event.getNewScreen()).input.getValue();
                } catch (Exception e) {
                    ConquestChatMod.LOGGER.error("[ClientEventHandler] Failed to get chat text", e);
                }

                // ПОДМЕНА
                event.setNewScreen(new CustomChatScreen(defaultText));
            }
        }
    }
}
