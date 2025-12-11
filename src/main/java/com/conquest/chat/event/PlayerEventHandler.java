package com.conquest.chat.event;

import com.conquest.chat.ConquestChatMod;
import com.conquest.chat.manager.ServerChannelManager;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ConquestChatMod.MOD_ID)
public class PlayerEventHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ServerChannelManager.removePlayer(player);
            ConquestChatMod.LOGGER.info("Removed channel data for player: " + player.getName().getString());
        }
    }
}
