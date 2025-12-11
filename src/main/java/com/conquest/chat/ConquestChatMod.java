package com.conquest.chat;

import com.conquest.chat.config.ChatConfig;
import com.conquest.chat.config.ServerConfig;
import com.conquest.chat.network.NetworkHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ConquestChatMod.MOD_ID)
public class ConquestChatMod {
    public static final String MOD_ID = "conquestchat";
    public static final Logger LOGGER = LogManager.getLogger();

    public ConquestChatMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Регистрируем метод для общей настройки (включая сеть)
        modEventBus.addListener(this::setup);

        // Регистрируем конфигурацию сервера
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        // Регистрируем сам мод в шине событий Forge
        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ChatConfig.CLIENT_SPEC);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // Регистрация сетевых пакетов должна происходить здесь, в потокобезопасном режиме
        event.enqueueWork(() -> {
            NetworkHandler.register();
            LOGGER.info("ConquestChat Network Registered Successfully!");
        });

        LOGGER.info("ConquestChat Common setup complete");
    }

}
