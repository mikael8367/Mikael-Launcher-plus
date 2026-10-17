package com.mikael.launcherplus;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/** Private, size-limited crash log storage. Never logs preferences or account/session data. */
public final class DiagnosticLogManager {
    private static final int MAX_LOG_BYTES = 256 * 1024;
    private final File logFile;
    private Thread.UncaughtExceptionHandler previousHandler;

    public DiagnosticLogManager(Context context) {
        File directory = new File(context.getFilesDir(), "diagnostics");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Não foi possível criar a pasta privada de diagnósticos.");
        }
        logFile = new File(directory, "last-crash.txt");
    }

    public void installCrashHandler() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                writeCrash(thread, throwable);
            } catch (Exception ignored) {
                // Logging must never prevent Android's original crash handler from running.
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            }
        });
    }

    public boolean hasCrashLog() {
        return logFile.isFile() && logFile.length() > 0;
    }

    public String readLastCrash() throws IOException {
        if (!hasCrashLog()) return "Nenhum log de falha salvo.";
        if (logFile.length() > MAX_LOG_BYTES) throw new IOException("O log excede o limite seguro.");
        byte[] bytes = new byte[(int) logFile.length()];
        try (java.io.FileInputStream input = new java.io.FileInputStream(logFile)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) break;
                offset += count;
            }
            return sanitize(new String(bytes, 0, offset, StandardCharsets.UTF_8));
        }
    }

    public String createSafeDiagnosticReport(String device, long totalRamMb, long availableRamMb,
                                             int preferredRamMb, String renderer, String appVersion) {
        return "Mikael Launcher Plus — diagnóstico\n"
                + "Data: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date()) + "\n"
                + "App: " + appVersion + "\n"
                + "Dispositivo: " + device + "\n"
                + "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "RAM total reportada: " + totalRamMb + " MB\n"
                + "RAM disponível no momento: " + availableRamMb + " MB\n"
                + "RAM preferida (não necessariamente aplicada ao motor): " + preferredRamMb + " MB\n"
                + "Renderizador preferido (não necessariamente aplicado ao motor): " + renderer + "\n"
                + "Log de falha salvo: " + (hasCrashLog() ? "sim" : "não") + "\n"
                + "Este relatório não inclui senhas, tokens ou dados de autenticação.\n";
    }

    private void writeCrash(Thread thread, Throwable throwable) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("Mikael Launcher Plus — falha não tratada\n")
                .append("Data: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append('\n')
                .append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                .append("Thread: ").append(thread == null ? "desconhecida" : thread.getName()).append('\n');
        if (throwable != null) {
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.StringWriter());
            java.io.StringWriter trace = new java.io.StringWriter();
            java.io.PrintWriter traceWriter = new java.io.PrintWriter(trace);
            throwable.printStackTrace(traceWriter);
            traceWriter.flush();
            report.append(sanitize(trace.toString()));
        } else {
            report.append("Throwable indisponível.\n");
        }
        String output = report.length() > MAX_LOG_BYTES
                ? report.substring(0, MAX_LOG_BYTES) : report.toString();
        try (FileOutputStream stream = new FileOutputStream(logFile, false)) {
            stream.write(output.getBytes(StandardCharsets.UTF_8));
            stream.flush();
        }
    }

    private String sanitize(String input) {
        if (input == null) return "";
        String output = input;
        output = Pattern.compile("(?i)(access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|authorization)(\\s*[=:]\\s*)[^\\s,;]+")
                .matcher(output).replaceAll("$1$2[REDACTED]");
        output = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*")
                .matcher(output).replaceAll("Bearer [REDACTED]");
        return output;
    }
}
