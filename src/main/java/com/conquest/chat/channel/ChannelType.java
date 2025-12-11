package com.conquest.chat.channel;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public enum ChannelType {
    ALL("Общий", ChatFormatting.WHITE, 0),
    TRADE("Торг", ChatFormatting.GOLD, 0),
    COMBAT("Бой", ChatFormatting.RED, 0),
    WHISPER("Личное", ChatFormatting.LIGHT_PURPLE, 0),
    GLOBAL("Глобальный", ChatFormatting.AQUA, 60); // 60 секунд кулдаун

    private final String displayName;
    private final ChatFormatting color;
    private final int cooldownSeconds;

    ChannelType(String displayName, ChatFormatting color, int cooldownSeconds) {
        this.displayName = displayName;
        this.color = color;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public MutableComponent getPrefix() {
        return Component.literal("[" + displayName + "] ").withStyle(color);
    }

    public boolean needsCooldown() {
        return cooldownSeconds > 0;
    }
}
