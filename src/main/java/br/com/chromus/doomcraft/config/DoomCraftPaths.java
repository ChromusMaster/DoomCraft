package br.com.chromus.doomcraft.config;

import br.com.chromus.doomcraft.DoomCraft;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DoomCraftPaths {
    public static final Path ROOT = FabricLoader.getInstance().getConfigDir().resolve("doomcraft");
    public static final Path WADS = ROOT.resolve("wads");
    public static final Path SAVES = ROOT.resolve("saves");
    public static final Path NATIVE = ROOT.resolve("native");
    public static final Path SESSIONS = ROOT.resolve("sessions");
    public static final Path LOGS = ROOT.resolve("logs");

    private DoomCraftPaths() {
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(WADS);
            Files.createDirectories(SAVES);
            Files.createDirectories(NATIVE);
            Files.createDirectories(SESSIONS);
            Files.createDirectories(LOGS);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create DoomCraft configuration directories", exception);
        }
    }
}
