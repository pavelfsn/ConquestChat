package com.conquest.chat.enums;

/**
 * Тип содержимого сообщения, а не вкладка.
 * Используется для фильтрации на клиенте.
 */
public enum ChatMessageType {
    GENERAL,   // Обычный / локальный / глобальный текст
    TRADE,     // Торговые сообщения
    WHISPER,   // Личные сообщения
    COMBAT     // Боевой лог
}
