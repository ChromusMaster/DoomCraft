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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NativeBinaryManager {
    private NativeBinaryManager() {
    }

    public static String platformId() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String normalizedArch = arch.contains("aarch64") || arch.contains("arm64")
                ? "arm64"
                : "x86_64";

        if (os.contains("win")) {
            return "windows-" + normalizedArch;
        }
        if (os.contains("mac")) {
            return "macos-" + normalizedArch;
        }
        return "linux-" + normalizedArch;
    }

    public static Path ensureExecutable() throws IOException {
        String platform = platformId();
        Path executableRelative = executableRelativePath(platform);
        Path targetDirectory = DoomCraftPaths.NATIVE.resolve(platform);
        Path executable = targetDirectory.resolve(executableRelative);

        List<String> bundledFiles = readManifest(platform);
        if (bundledFiles.isEmpty()) {
            throw new IOException(
                    "Pacote nativo LZDoom ausente para " + platform
                            + ". Use o JAR multiplataforma gerado pelo GitHub Actions."
            );
        }

        String expectedBuildId = readResourceText(
                "/natives/" + platform + "/native-build-id.txt"
        ).trim();
        Path installedBuildId = targetDirectory.resolve("native-build-id.txt");

        if (Files.isRegularFile(executable)
                && Files.isRegularFile(installedBuildId)
                && Files.readString(installedBuildId, StandardCharsets.UTF_8).trim()
                .equals(expectedBuildId)) {
            executable.toFile().setExecutable(true, true);
            return executable;
        }

        clearDirectory(targetDirectory);
        Files.createDirectories(targetDirectory);

        for (String relativeName : bundledFiles) {
            validateManifestEntry(relativeName);
            String normalizedName = relativeName.replace('\\', '/');
            String resource = "/natives/" + platform + "/" + normalizedName;
            Path target = targetDirectory.resolve(relativeName).normalize();
            if (!target.startsWith(targetDirectory)) {
                throw new IOException(
                        "Caminho nativo escapou do diretório permitido: " + relativeName
                );
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = NativeBinaryManager.class.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("Recurso nativo listado mas ausente: " + resource);
                }
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        if (!Files.isRegularFile(executable)) {
            throw new IOException("Executável não encontrado após extração: " + executable);
        }
        executable.toFile().setExecutable(true, true);
        return executable;
    }

    private static Path executableRelativePath(String platform) throws IOException {
        if (platform.equals("windows-x86_64")) {
            return Path.of("lzdoom.exe");
        }
        if (platform.equals("linux-x86_64")) {
            return Path.of("lzdoom");
        }
        if (platform.equals("macos-x86_64") || platform.equals("macos-arm64")) {
            return Path.of("lzdoom.app", "Contents", "MacOS", "lzdoom");
        }
        throw new IOException("Plataforma nativa não suportada nesta versão: " + platform);
    }

    private static void validateManifestEntry(String relativeName) throws IOException {
        if (relativeName.isBlank()
                || relativeName.contains("..")
                || relativeName.startsWith("/")
                || relativeName.startsWith("\\")) {
            throw new IOException("Entrada inválida no manifesto nativo: " + relativeName);
        }
    }

    private static void clearDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String readResourceText(String resource) throws IOException {
        try (InputStream input = NativeBinaryManager.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Recurso nativo ausente: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> readManifest(String platform) throws IOException {
        String resource = "/natives/" + platform + "/native-manifest.txt";
        try (InputStream input = NativeBinaryManager.class.getResourceAsStream(resource)) {
            if (input == null) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        result.add(line);
                    }
                }
            }
            return result;
        }
    }
}
