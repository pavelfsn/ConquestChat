package com.conquest.chat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class ChatTabLogger {
    private static final ChatTabLogger INSTANCE = new ChatTabLogger();

    // §x§R§R§G§G§B§B (hex) + обычные §a, §l, §r и т.п.
    private static final Pattern HEX_COLOR = Pattern.compile("§x(§[0-9a-fA-F]){6}");
    private static final Pattern LEGACY_CODES = Pattern.compile("§[0-9a-fA-Fk-orK-OR]");

    // Чтобы логи не росли бесконечно — по умолчанию делаем по дням
    private static final boolean DAILY_FILES = true;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ConquestChat-Logger");
        t.setDaemon(true);
        return t;
    });

    public static ChatTabLogger get() {
        return INSTANCE;
    }

    private ChatTabLogger() {}

    /** Папка логов: <gameDir>/conquestchat-logs/ */
    private Path logsDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("conquestchat-logs");
    }

    /** Маппинг русских вкладок в привычные имена файлов */
    private String fileNameForTab(String tabName) {
        if (tabName == null) return "unknown";

        // Под твои текущие вкладки из ClientChatManager
        switch (tabName) {
            case "Общий" -> { return "general"; }
            case "Торговый" -> { return "trade"; }
            case "Личное" -> { return "private"; }
            case "Урон" -> { return "combat"; }
        }

        // На будущее: если появятся новые вкладки
        return sanitize(tabName);
    }

    private String sanitize(String s) {
        if (s == null || s.isBlank()) return "unknown";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private Path resolveLogFile(String tabName) {
        String base = fileNameForTab(tabName);
        if (DAILY_FILES) {
            base = base + "-" + LocalDate.now().format(DATE_FMT);
        }
        return logsDir().resolve(base + ".log");
    }

    /** Превращаем компонент в простой текст и чистим любые §-коды */
    private String toPlainLine(Component msg) {
        if (msg == null) return "";
        String s = msg.getString(); // обычно уже без стилей, но § в literal-строках останется
        if (s == null) return "";

        // 1) stripFormatting (на всякий) + 2) прямое удаление §-кодов
        String out = ChatFormatting.stripFormatting(s);
        if (out == null) out = s;

        out = HEX_COLOR.matcher(out).replaceAll("");
        out = LEGACY_CODES.matcher(out).replaceAll("");

        // легкая нормализация (чтобы не было "пустых" строк из пробелов)
        out = out.replace('\t', ' ').trim();
        return out;
    }

    public void append(String tabName, Component msg) {
        String line = toPlainLine(msg);
        if (line.isEmpty()) return;

        io.execute(() -> {
            try {
                Files.createDirectories(logsDir());
                Path file = resolveLogFile(tabName);

                Files.writeString(
                        file,
                        line + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND
                );
            } catch (IOException ignored) {
                // Можно логировать в LOGGER.debug, но лучше молча, чтобы не спамить консоль.
            }
        });
    }

    /** Опционально: дергать на выходе из игры, не обязательно */
    public void shutdown() {
        io.shutdown();
    }
}
