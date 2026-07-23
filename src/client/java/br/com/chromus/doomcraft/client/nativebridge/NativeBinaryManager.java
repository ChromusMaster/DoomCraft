package br.com.chromus.doomcraft.client.nativebridge;

import br.com.chromus.doomcraft.config.DoomCraftPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NativeBinaryManager {
    private NativeBinaryManager() {
    }

    public static String platformId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String normalizedArch = arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x86_64";

        if (os.contains("win")) return "windows-" + normalizedArch;
        if (os.contains("mac")) return "macos-" + normalizedArch;
        return "linux-" + normalizedArch;
    }

    public static Path ensureExecutable() throws IOException {
        String platform = platformId();
        boolean windows = platform.startsWith("windows");
        String executableName = windows ? "lzdoom.exe" : "lzdoom";
        Path targetDirectory = DoomCraftPaths.NATIVE.resolve(platform);
        Files.createDirectories(targetDirectory);

        List<String> bundledFiles = readManifest(platform);
        if (bundledFiles.isEmpty()) {
            throw new IOException(
                    "Pacote nativo LZDoom ausente para " + platform
                            + ". Execute ./gradlew buildNative e depois ./gradlew build nesse sistema."
            );
        }

        for (String relativeName : bundledFiles) {
            if (relativeName.isBlank() || relativeName.contains("..") || relativeName.startsWith("/")) {
                throw new IOException("Entrada inválida no manifesto nativo: " + relativeName);
            }
            String resource = "/natives/" + platform + "/" + relativeName.replace('\\', '/');
            Path target = targetDirectory.resolve(relativeName).normalize();
            if (!target.startsWith(targetDirectory)) {
                throw new IOException("Caminho nativo escapou do diretório permitido: " + relativeName);
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = NativeBinaryManager.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("Recurso nativo listado mas ausente: " + resource);
                }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Path executable = targetDirectory.resolve(executableName);
        if (!Files.isRegularFile(executable)) {
            throw new IOException("Executável não encontrado após extração: " + executable);
        }
        executable.toFile().setExecutable(true, true);
        return executable;
    }

    private static List<String> readManifest(String platform) throws IOException {
        String resource = "/natives/" + platform + "/native-manifest.txt";
        try (InputStream input = NativeBinaryManager.class.getResourceAsStream(resource)) {
            if (input == null) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) result.add(line);
                }
            }
            return result;
        }
    }
}
