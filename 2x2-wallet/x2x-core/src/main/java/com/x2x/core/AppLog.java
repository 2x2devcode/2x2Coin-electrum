package com.x2x.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Append-only diagnostic log for send/broadcast troubleshooting.
 *
 * <p>Windows (preferred): {@code %LOCALAPPDATA%\2x2-Wallet\logs\wallet.log}
 * <br>Other platforms: {@code ~/.2x2-wallet/logs/wallet.log}
 *
 * <p>Never writes seeds, PINs, or private keys — only addresses, amounts, tx hex, and API errors.
 */
public final class AppLog {

    public static final String APP_VERSION = "1.3.13";
    private static final long MAX_BYTES = 2_000_000L; // rotate when larger
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static volatile Path logFile;
    private static volatile Path logDir;

    private AppLog() {}

    /** Directory that contains {@code wallet.log} (created on first write). */
    public static Path logDirectory() {
        ensureInit();
        return logDir;
    }

    /** Absolute path to the active log file. */
    public static Path logFile() {
        ensureInit();
        return logFile;
    }

    /** Human-readable path for UI (Windows-style env var when applicable). */
    public static String logFileDisplayPath() {
        ensureInit();
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            try {
                Path base = Path.of(local).toAbsolutePath().normalize();
                Path file = logFile.toAbsolutePath().normalize();
                if (file.startsWith(base)) {
                    return "%LOCALAPPDATA%\\2x2-Wallet\\logs\\wallet.log";
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return logFile.toAbsolutePath().toString();
    }

    /**
     * Override log directory (Android {@code filesDir}, tests). Pass {@code null} to reset.
     */
    public static void setLogDirectory(Path dir) {
        LOCK.lock();
        try {
            if (dir == null) {
                logDir = null;
                logFile = null;
                return;
            }
            logDir = dir;
            logFile = dir.resolve("wallet.log");
        } finally {
            LOCK.unlock();
        }
    }

    public static void info(String message) {
        write("INFO", message, null);
    }

    public static void warn(String message) {
        write("WARN", message, null);
    }

    public static void error(String message) {
        write("ERROR", message, null);
    }

    public static void error(String message, Throwable t) {
        write("ERROR", message, t);
    }

    private static void ensureInit() {
        if (logFile != null) return;
        LOCK.lock();
        try {
            if (logFile != null) return;
            logDir = defaultLogDir();
            logFile = logDir.resolve("wallet.log");
        } finally {
            LOCK.unlock();
        }
    }

    static Path defaultLogDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null && !local.isBlank()) {
            return Path.of(local, "2x2-Wallet", "logs");
        }
        return Path.of(System.getProperty("user.home"), ".2x2-wallet", "logs");
    }

    private static void write(String level, String message, Throwable t) {
        ensureInit();
        StringBuilder line = new StringBuilder(160);
        line.append(TS.format(Instant.now()))
                .append(" [").append(level).append("] ")
                .append("v").append(APP_VERSION).append(' ')
                .append(message == null ? "" : message);
        if (t != null) {
            line.append(" | ").append(t.getClass().getSimpleName())
                    .append(": ").append(t.getMessage());
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            line.append('\n').append(sw);
        }
        line.append('\n');

        LOCK.lock();
        try {
            Files.createDirectories(logDir);
            rotateIfNeeded();
            Files.writeString(logFile, line.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must never break payments.
        } finally {
            LOCK.unlock();
        }
    }

    private static void rotateIfNeeded() throws IOException {
        if (!Files.isRegularFile(logFile)) return;
        if (Files.size(logFile) < MAX_BYTES) return;
        Path bak = logDir.resolve("wallet.log.1");
        Files.deleteIfExists(bak);
        Files.move(logFile, bak);
    }

    /** Truncate long strings for compact log lines. */
    public static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…(len=" + s.length() + ")";
    }
}
