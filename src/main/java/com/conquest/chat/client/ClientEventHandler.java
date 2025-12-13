package com.conquest.chat.client;

import com.conquest.chat.ConquestChatMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        if (KeyBindings.SWITCH_CHANNEL != null) {
            event.register(KeyBindings.SWITCH_CHANNEL);
        }
    }

    // Вложенный класс для событий Forge (экраны, HUD)
    @Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onOpenScreen(ScreenEvent.Opening event) {
            // Подмена ванильного ChatScreen на наш CustomChatScreen
            if (event.getNewScreen() instanceof ChatScreen && !(event.getNewScreen() instanceof CustomChatScreen)) {
                ConquestChatMod.LOGGER.info("[ClientEventHandler] Intercepted ChatScreen opening. Replacing with CustomChatScreen.");

                String defaultText = "";
                try {
                    // defaultText = ((ChatScreen) event.getNewScreen()).input.getValue();
                } catch (Exception e) {
                    ConquestChatMod.LOGGER.error("[ClientEventHandler] Failed to get chat text", e);
                }

                event.setNewScreen(new CustomChatScreen(defaultText));
            }
        }

        @SubscribeEvent
        public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // Когда чат/GUI открыт — HUD-дубликат не рисуем
            if (mc.screen != null) return;

            GuiGraphics gg = event.getGuiGraphics();

            int maxLines = 7;
            long lifetimeMs = 7000;

            // ВАЖНО: теперь метод существует и сам фильтрует по activeTabName
            List<Component> lines = ClientChatManager.getInstance().getHudMessages(maxLines, lifetimeMs);
            if (lines.isEmpty()) return;

            int x = 4;
            int lineH = mc.font.lineHeight + 1;

            // Рисуем “над хотбаром”
            int y = mc.getWindow().getGuiScaledHeight() - 40 - (lines.size() * lineH);

            for (Component c : lines) {
                gg.drawString(mc.font, c, x, y, 0xFFFFFFFF, true);
                y += lineH;
            }
        }
    }
}
