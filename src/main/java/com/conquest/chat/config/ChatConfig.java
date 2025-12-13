package com.conquest.chat.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ChatConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        final Pair<Client, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = specPair.getRight();
        CLIENT = specPair.getLeft();
    }

    public static class Client {
        public final ForgeConfigSpec.IntValue chatWidth;
        public final ForgeConfigSpec.IntValue chatHeight;
        public final ForgeConfigSpec.IntValue fadeDuration;
        public final ForgeConfigSpec.ConfigValue<String> activeTabColor;
        public final ForgeConfigSpec.ConfigValue<String> inactiveTabColor;
        public final ForgeConfigSpec.ConfigValue<String> backgroundColor;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.push("general");

            // Ближе к референсу: уже и выше
            chatWidth = builder.comment("Ширина окна чата")
                    .defineInRange("chatWidth", 240, 200, 1000);

            chatHeight = builder.comment("Высота окна чата")
                    .defineInRange("chatHeight", 240, 100, 1000);

            fadeDuration = builder.comment("Длительность анимации появления (мс)")
                    .defineInRange("fadeDuration", 150, 0, 2000);

            activeTabColor = builder.comment("Цвет активной вкладки (ARGB Hex)")
                    .define("activeTabColor", "#FFFFFFFF");

            inactiveTabColor = builder.comment("Цвет неактивной вкладки (ARGB Hex)")
                    .define("inactiveTabColor", "#FFAAAAAA");

            backgroundColor = builder.comment("Цвет фона чата (ARGB Hex)")
                    .define("backgroundColor", "#80000000");

            builder.pop();
        }

    }

    // Вспомогательный метод для парсинга цвета
    public static int getColor(ForgeConfigSpec.ConfigValue<String> configValue) {
        try {
            String hex = configValue.get();
            if (hex.startsWith("#")) hex = hex.substring(1);
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }
}
