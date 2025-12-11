package com.conquest.chat.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    // Настройки каналов
    public static final ForgeConfigSpec.IntValue GLOBAL_COOLDOWN;
    public static final ForgeConfigSpec.IntValue TRADE_COOLDOWN;
    public static final ForgeConfigSpec.IntValue LOCAL_COOLDOWN;
    public static final ForgeConfigSpec.IntValue TRADE_RADIUS;
    public static final ForgeConfigSpec.IntValue LOCAL_RADIUS;

    // Настройки боевого лога
    public static final ForgeConfigSpec.BooleanValue COMBAT_LOG_ENABLED;
    public static final ForgeConfigSpec.BooleanValue LOG_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue LOG_KILLS;
    public static final ForgeConfigSpec.DoubleValue MIN_DAMAGE;

    // Настройки анти-спама
    public static final ForgeConfigSpec.BooleanValue ANTI_SPAM_ENABLED;
    public static final ForgeConfigSpec.BooleanValue DUPLICATE_CHECK;
    public static final ForgeConfigSpec.IntValue MAX_DUPLICATES;

    // Настройки battle log файла
    public static final ForgeConfigSpec.BooleanValue BATTLE_LOG_FILE_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_LOG_FILE_SIZE_MB;
    public static final ForgeConfigSpec.BooleanValue AUTO_COMPRESS_OLD_LOGS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        // === НАСТРОЙКИ КАНАЛОВ ===
        builder.comment("Channel Settings").push("channels");

        GLOBAL_COOLDOWN = builder
                .comment("Cooldown for Global chat in seconds")
                .defineInRange("global_cooldown", 30, 0, 3600);

        TRADE_COOLDOWN = builder
                .comment("Cooldown for Trade chat in seconds")
                .defineInRange("trade_cooldown", 10, 0, 3600);

        LOCAL_COOLDOWN = builder
                .comment("Cooldown for Local chat in seconds")
                .defineInRange("local_cooldown", 1, 0, 3600);

        TRADE_RADIUS = builder
                .comment("Radius for Trade chat in blocks")
                .defineInRange("trade_radius", 100, 1, 10000);

        LOCAL_RADIUS = builder
                .comment("Radius for Local chat in blocks")
                .defineInRange("local_radius", 100, 1, 10000);

        builder.pop();

        // === НАСТРОЙКИ БОЕВОГО ЛОГА ===
        builder.comment("Combat Log Settings").push("combat_log");

        COMBAT_LOG_ENABLED = builder
                .comment("Enable combat logging in chat")
                .define("enabled", true);

        LOG_DAMAGE = builder
                .comment("Log damage events")
                .define("log_damage", true);

        LOG_KILLS = builder
                .comment("Log kill events")
                .define("log_kills", true);

        MIN_DAMAGE = builder
                .comment("Minimum damage to log")
                .defineInRange("min_damage", 0.5, 0.0, 1000.0);

        builder.pop();

        // === НАСТРОЙКИ АНТИ-СПАМА ===
        builder.comment("Anti-Spam Settings").push("anti_spam");

        ANTI_SPAM_ENABLED = builder
                .comment("Enable anti-spam system")
                .define("enabled", true);

        DUPLICATE_CHECK = builder
                .comment("Check for duplicate messages")
                .define("duplicate_check", true);

        MAX_DUPLICATES = builder
                .comment("Maximum number of duplicate messages allowed in a row")
                .defineInRange("max_duplicates", 3, 1, 10);

        builder.pop();

        // === НАСТРОЙКИ BATTLE LOG ФАЙЛА ===
        builder.comment("Battle Log File Settings").push("battle_log_file");

        BATTLE_LOG_FILE_ENABLED = builder
                .comment("Enable battle log file creation")
                .define("enabled", true);

        MAX_LOG_FILE_SIZE_MB = builder
                .comment("Maximum log file size in MB before rotation")
                .defineInRange("max_file_size_mb", 100, 1, 1000);

        AUTO_COMPRESS_OLD_LOGS = builder
                .comment("Automatically compress old log files")
                .define("auto_compress", true);

        builder.pop();

        SPEC = builder.build();
    }
}
