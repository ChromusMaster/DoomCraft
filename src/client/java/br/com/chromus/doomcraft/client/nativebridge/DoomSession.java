package br.com.chromus.doomcraft.client.nativebridge;

import br.com.chromus.doomcraft.DoomCraft;
import br.com.chromus.doomcraft.config.DoomCraftPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns one isolated native LZDoom process. Only one instance is created by the
 * client runtime, even if several television blocks exist in the world.
 */
public final class DoomSession implements AutoCloseable {
    public static final long NATIVE_RSS_LIMIT_BYTES = 768L * 1024L * 1024L;
    private static final long LINUX_ADDRESS_SPACE_LIMIT_BYTES = 960L * 1024L * 1024L;

    private final UUID televisionId;
    private final List<Path> wads;
    private final Path sessionDirectory;
    private final Path commandsDirectory;
    private final Path saveDirectory;
    private final Path frameFile;
    private final Path logFile;
    private final String saveSlot;
    private final AtomicLong commandSequence = new AtomicLong();
    private final FrameFileReader frameReader = new FrameFileReader();

    private Process process;
    private boolean paused;
    private boolean loadedExistingSave;
    private int startupTicks;
    private int memoryProbeDivider;
    private String failureReason;

    public DoomSession(UUID televisionId, List<Path> wads) {
        if (wads.isEmpty()) throw new IllegalArgumentException("At least one WAD is required");
        this.televisionId = televisionId;
        this.wads = wads.stream().map(path -> path.toAbsolutePath().normalize()).toList();
        this.sessionDirectory = DoomCraftPaths.SESSIONS.resolve(televisionId.toString());
        this.commandsDirectory = sessionDirectory.resolve("commands");
        this.saveDirectory = DoomCraftPaths.SAVES.resolve(televisionId.toString());
        this.frameFile = sessionDirectory.resolve("frame.bin");
        this.logFile = DoomCraftPaths.LOGS.resolve("lzdoom-" + televisionId + ".log");
        this.saveSlot = "doomcraft_" + televisionId.toString().replace("-", "");
    }

    public UUID televisionId() {
        return televisionId;
    }

    public List<Path> wads() {
        return wads;
    }

    public Path frameFile() {
        return frameFile;
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    public boolean isPaused() {
        return paused;
    }

    public String failureReason() {
        return failureReason;
    }

    public void start() throws IOException {
        if (isAlive()) {
            return;
        }

        Files.createDirectories(sessionDirectory);
        Files.createDirectories(commandsDirectory);
        Files.createDirectories(saveDirectory);
        Files.createDirectories(logFile.getParent());
        cleanTransientFiles();

        Path executable = NativeBinaryManager.ensureExecutable();
        List<String> lzdoomArguments = new ArrayList<>();
        lzdoomArguments.add(executable.toString());
        lzdoomArguments.add("-iwad");
        lzdoomArguments.add(wads.getFirst().toString());
        if (wads.size() > 1) {
            lzdoomArguments.add("-file");
            wads.stream().skip(1).map(Path::toString).forEach(lzdoomArguments::add);
        }
        lzdoomArguments.add("-savedir");
        lzdoomArguments.add(saveDirectory.toString());
        lzdoomArguments.add("-config");
        lzdoomArguments.add(sessionDirectory.resolve("lzdoom.ini").toString());
        lzdoomArguments.add("-width");
        lzdoomArguments.add(Integer.toString(DoomDynamicTexture.WIDTH));
        lzdoomArguments.add("-height");
        lzdoomArguments.add(Integer.toString(DoomDynamicTexture.HEIGHT));
        lzdoomArguments.add("-noautoload");
        lzdoomArguments.add("-nojoy");
        lzdoomArguments.add("+vid_preferbackend");
        lzdoomArguments.add("2");
        lzdoomArguments.add("+vid_maxfps");
        lzdoomArguments.add("30");

        List<String> command = applyOperatingSystemLimit(lzdoomArguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(executable.getParent().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        builder.environment().put("DOOMCRAFT_BRIDGE_DIR", sessionDirectory.toString());
        builder.environment().put("DOOMCRAFT_SESSION_ID", televisionId.toString());
        builder.environment().put("SDL_VIDEO_MINIMIZE_ON_FOCUS_LOSS", "0");
        builder.environment().putIfAbsent("SDL_VIDEODRIVER", "dummy");

        Files.writeString(
                logFile,
                "\n=== DoomCraft native session " + Instant.now() + " ===\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );

        process = builder.start();
        paused = false;
        loadedExistingSave = false;
        startupTicks = 0;
        memoryProbeDivider = 0;
        failureReason = null;
        DoomCraft.LOGGER.info("Started LZDoom PID {} for TV {} using {}", process.pid(), televisionId, wads);
    }

    private static List<String> applyOperatingSystemLimit(List<String> original) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path prlimit = Path.of("/usr/bin/prlimit");
        if (os.contains("linux") && Files.isExecutable(prlimit)) {
            List<String> limited = new ArrayList<>();
            limited.add(prlimit.toString());
            limited.add("--as=" + LINUX_ADDRESS_SPACE_LIMIT_BYTES);
            limited.add("--rss=" + NATIVE_RSS_LIMIT_BYTES);
            limited.add("--");
            limited.addAll(original);
            return limited;
        }
        return original;
    }

    public void tick(DoomDynamicTexture texture) {
        if (!isAlive()) {
            if (process != null && failureReason == null) {
                failureReason = "LZDoom encerrou com código " + process.exitValue() + ". Consulte " + logFile;
            }
            return;
        }

        startupTicks++;
        if (!loadedExistingSave && startupTicks >= 30) {
            loadedExistingSave = true;
            if (hasExistingSave()) {
                sendCommand("LOAD " + saveSlot);
            }
        }

        frameReader.read(frameFile).ifPresent(texture::upload);

        if (++memoryProbeDivider >= 20) {
            memoryProbeDivider = 0;
            long rss = ProcessMemoryProbe.residentBytes(process).orElse(-1L);
            if (rss > NATIVE_RSS_LIMIT_BYTES) {
                failureReason = "LZDoom excedeu o limite de 768 MiB RSS (" + rss + " bytes).";
                DoomCraft.LOGGER.error("{} TV={}", failureReason, televisionId);
                stopImmediately();
            }
        }
    }

    public void pauseAndSave() {
        if (!isAlive() || paused) {
            return;
        }
        save();
        sendCommand("PAUSE");
        releaseAllActions();
        paused = true;
    }

    public void resume() {
        if (!isAlive()) {
            return;
        }
        if (paused) {
            sendCommand("RESUME");
            paused = false;
        }
    }

    public void save() {
        if (isAlive()) {
            sendCommand("SAVE " + saveSlot);
        }
    }

    public void requestQuit() {
        if (isAlive()) {
            releaseAllActions();
            sendCommand("QUIT");
        }
    }

    public void setAction(String action, boolean pressed) {
        if (isAlive() && !paused) {
            sendCommand("ACTION " + action + " " + (pressed ? "1" : "0"));
        }
    }

    public void pulseCommand(String command) {
        if (isAlive() && !paused) {
            sendCommand("COMMAND " + command);
        }
    }

    public void releaseAllActions() {
        for (String action : List.of("forward", "back", "left", "right", "attack", "use", "speed")) {
            sendCommand("ACTION " + action + " 0");
        }
    }

    private void sendCommand(String command) {
        if (!isAlive()) {
            return;
        }
        try {
            Files.createDirectories(commandsDirectory);
            long sequence = commandSequence.incrementAndGet();
            Path temporary = commandsDirectory.resolve(String.format(Locale.ROOT, "%020d.tmp", sequence));
            Path target = commandsDirectory.resolve(String.format(Locale.ROOT, "%020d.cmd", sequence));
            Files.writeString(
                    temporary,
                    command + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            DoomCraft.LOGGER.error("Could not send native command '{}'", command, exception);
        }
    }

    private boolean hasExistingSave() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(saveDirectory)) {
            return stream.iterator().hasNext();
        } catch (IOException ignored) {
            return false;
        }
    }

    private void cleanTransientFiles() throws IOException {
        Files.deleteIfExists(frameFile);
        if (Files.isDirectory(commandsDirectory)) {
            try (var paths = Files.list(commandsDirectory)) {
                for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    public void stopGracefully(long timeoutMillis) {
        if (process == null) {
            return;
        }
        requestQuit();
        try {
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroy();
            }
            if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            process = null;
            paused = false;
        }
    }

    public void stopImmediately() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        paused = false;
    }

    @Override
    public void close() {
        stopGracefully(1200);
    }
}
