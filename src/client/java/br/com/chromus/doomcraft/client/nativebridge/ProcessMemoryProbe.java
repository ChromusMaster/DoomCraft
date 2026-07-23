package br.com.chromus.doomcraft.client.nativebridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.OptionalLong;

public final class ProcessMemoryProbe {
    private ProcessMemoryProbe() {
    }

    public static OptionalLong residentBytes(Process process) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        long pid = process.pid();

        if (os.contains("linux")) {
            return linuxResidentBytes(pid);
        }
        if (os.contains("mac")) {
            return commandResidentBytes(new String[]{"ps", "-o", "rss=", "-p", Long.toString(pid)});
        }
        if (os.contains("win")) {
            return commandResidentBytes(new String[]{
                    "powershell", "-NoProfile", "-Command",
                    "(Get-Process -Id " + pid + ").WorkingSet64"
            }, false);
        }
        return OptionalLong.empty();
    }

    private static OptionalLong linuxResidentBytes(long pid) {
        Path status = Path.of("/proc", Long.toString(pid), "status");
        try {
            for (String line : Files.readAllLines(status)) {
                if (line.startsWith("VmRSS:")) {
                    String digits = line.replaceAll("[^0-9]", "");
                    return OptionalLong.of(Long.parseLong(digits) * 1024L);
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return OptionalLong.empty();
    }

    private static OptionalLong commandResidentBytes(String[] command) {
        return commandResidentBytes(command, true);
    }

    private static OptionalLong commandResidentBytes(String[] command, boolean kilobytes) {
        try {
            Process probe = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(probe.getInputStream()))) {
                String text = reader.readLine();
                if (text == null) {
                    return OptionalLong.empty();
                }
                long value = Long.parseLong(text.trim());
                return OptionalLong.of(kilobytes ? value * 1024L : value);
            }
        } catch (IOException | NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }
}
