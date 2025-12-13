package com.conquest.chat.mixin.client;

import com.conquest.chat.client.ClientChatManager;
import com.conquest.chat.enums.ChatMessageType;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Перенаправляет сообщения из ванильного ChatComponent (HUD чат)
 * в твой ClientChatManager и не даёт ваниле сохранять их у себя.
 */
@Mixin(ChatComponent.class)
public class MixinChatComponent {

    @Unique
    private static boolean conquestchat$reentryGuard = false;

    // 1) Простая перегрузка (часто локальные сообщения)
    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void conquestchat$onAddMessageSimple(Component message, CallbackInfo ci) {
        if (conquestchat$reentryGuard) {
            ci.cancel();
            return;
        }

        conquestchat$reentryGuard = true;
        try {
            ClientChatManager.getInstance().addMessage(ChatMessageType.SYSTEM, message);
        } finally {
            conquestchat$reentryGuard = false;
        }

        ci.cancel();
    }

    /**
     * 2) Внутренняя/полная перегрузка (в 1.20.1 она с boolean в конце).
     * Сигнатура с boolean встречается в ваниле вокруг GuiMessage/MessageSignature. [web:105]
     */
    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void conquestchat$onAddMessageFull(Component message,
                                               @Nullable MessageSignature signature,
                                               int ticks,
                                               @Nullable GuiMessageTag tag,
                                               boolean refresh,
                                               CallbackInfo ci) {
        if (conquestchat$reentryGuard) {
            ci.cancel();
            return;
        }

        conquestchat$reentryGuard = true;
        try {
            ClientChatManager.getInstance().addMessage(classify(message, signature, tag), message);
        } finally {
            conquestchat$reentryGuard = false;
        }

        ci.cancel();
    }

    @Unique
    private static ChatMessageType classify(Component message, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag) {
        // Если есть tag — это чаще системные/помеченные сообщения (debug, подсказки, etc.)
        if (tag != null) return ChatMessageType.SYSTEM;

        // Если есть signature — обычно “настоящий чат”, пусть будет GENERAL
        if (signature != null) return ChatMessageType.GENERAL;

        // Фоллбек по тексту
        String s = message.getString();
        if (s == null) return ChatMessageType.SYSTEM;
        s = s.trim();

        if (s.startsWith("<") || s.matches("^[A-Za-z0-9_]{3,16}:\\s.*")) return ChatMessageType.GENERAL;
        return ChatMessageType.SYSTEM;
    }
}
