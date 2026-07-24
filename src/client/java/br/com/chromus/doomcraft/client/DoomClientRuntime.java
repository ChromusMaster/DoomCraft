package br.com.chromus.doomcraft.client;

import br.com.chromus.doomcraft.DoomCraft;
import br.com.chromus.doomcraft.block.DoomTelevisionBlock;
import br.com.chromus.doomcraft.block.DoomTelevisionBlockEntity;
import br.com.chromus.doomcraft.block.TelevisionMode;
import br.com.chromus.doomcraft.client.nativebridge.DoomDynamicTexture;
import br.com.chromus.doomcraft.client.nativebridge.DoomSession;
import br.com.chromus.doomcraft.config.DoomCraftPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/** Client-only orchestration for proximity, native process lifetime and input. */
public final class DoomClientRuntime {
    private static final DoomClientRuntime INSTANCE = new DoomClientRuntime();
    private static final int WAD_NOTICE_TICKS = 20 * 30;
    private static final int SCAN_INTERVAL_TICKS = 10;
    private static final int STOP_DELAY_TICKS = 12;
    private static final DateTimeFormatter MANUAL_SAVE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final DoomDynamicTexture dynamicTexture = new DoomDynamicTexture();
    private final Map<String, Boolean> inputStates = new HashMap<>();

    private DoomSession session;
    private BlockPos selectedTelevision;
    private int worldTicks;
    private int scanDivider;
    private int pendingStopTicks = -1;
    private boolean inputFocused;
    private boolean noticeShown;
    private String lastError;
    private java.util.List<Path> cachedWads = java.util.List.of();

    private DoomClientRuntime() {
    }

    public static DoomClientRuntime getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        // Somente operações sem dependência do dispositivo gráfico.
        // O entrypoint Fabric é executado antes de RenderSystem.getDevice()
        // estar disponível no Minecraft 26.2.
        DoomCraftPaths.ensureDirectories();
    }

    public void onWorldJoin() {
        // A conexão com um mundo ocorre depois da inicialização do dispositivo
        // gráfico. Neste ponto DynamicTexture pode criar o recurso de GPU.
        dynamicTexture.register();

        worldTicks = 0;
        scanDivider = 0;
        pendingStopTicks = -1;
        noticeShown = false;
        inputFocused = false;
        inputStates.clear();
        lastError = null;
        cachedWads = findWads();
        stopSessionNow();
    }

    public void onWorldLeave() {
        inputFocused = false;
        inputStates.clear();
        if (session != null) {
            session.save();
            session.stopGracefully(1500);
            session = null;
        }
        selectedTelevision = null;
        pendingStopTicks = -1;
    }

    public void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }

        worldTicks++;
        showWadNoticeWhenDue(client);

        if (++scanDivider >= SCAN_INTERVAL_TICKS) {
            scanDivider = 0;
            updateProximityState(client);
        }

        if (session != null) {
            session.tick(dynamicTexture);
            if (!session.isAlive() && session.failureReason() != null) {
                lastError = session.failureReason();
                client.player.sendSystemMessage(Component.literal("DoomCraft: " + lastError));
                session = null;
                inputFocused = false;
                inputStates.clear();
            }
        }

        if (pendingStopTicks >= 0 && --pendingStopTicks <= 0) {
            stopSessionNow();
        }
    }

    private void showWadNoticeWhenDue(Minecraft client) {
        if (!noticeShown && worldTicks >= WAD_NOTICE_TICKS) {
            noticeShown = true;
            client.player.sendSystemMessage(
                    Component.translatable("message.doomcraft.wad_notice", DoomCraftPaths.WADS.toAbsolutePath())
            );
        }
    }

    private void updateProximityState(Minecraft client) {
        cachedWads = findWads();
        LocatedTelevision nearest = findNearestTelevision(client);
        if (nearest == null || nearest.distanceSquared > 64.0 || nearest.mode == TelevisionMode.BROKEN) {
            selectedTelevision = nearest == null ? null : nearest.pos;
            scheduleUnload();
            return;
        }

        selectedTelevision = nearest.pos;

        if (nearest.distanceSquared <= 16.0) {
            activate(nearest, client);
        } else {
            hibernate(nearest);
        }
    }

    private void activate(LocatedTelevision television, Minecraft client) {
        pendingStopTicks = -1;
        if (session == null || !session.televisionId().equals(television.sessionId)) {
            if (session != null) {
                session.save();
                session.stopGracefully(1000);
                session = null;
            }

            if (cachedWads.isEmpty()) {
                lastError = Component.translatable("message.doomcraft.no_wad", DoomCraftPaths.WADS.toAbsolutePath()).getString();
                return;
            }

            try {
                session = new DoomSession(television.sessionId, cachedWads);
                session.start();
                lastError = null;
            } catch (IOException exception) {
                lastError = exception.getMessage();
                DoomCraft.LOGGER.error("Could not start DoomCraft native session", exception);
                client.player.sendSystemMessage(Component.literal("DoomCraft: " + lastError));
                session = null;
                return;
            }
        }

        session.resume();
    }

    private void hibernate(LocatedTelevision television) {
        pendingStopTicks = -1;
        if (session != null && session.televisionId().equals(television.sessionId)) {
            session.pauseAndSave();
        } else if (session != null) {
            session.save();
            session.stopGracefully(1200);
            session = null;
        }
        releaseAllInput();
    }

    private void scheduleUnload() {
        releaseAllInput();
        if (session != null && pendingStopTicks < 0) {
            if (session.isPaused()) session.save();
            else session.pauseAndSave();
            pendingStopTicks = STOP_DELAY_TICKS;
        }
    }

    private LocatedTelevision findNearestTelevision(Minecraft client) {
        BlockPos origin = client.player.blockPosition();
        LocatedTelevision best = null;

        for (int x = -8; x <= 8; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockEntity candidate = client.level.getBlockEntity(pos);
                    if (!(candidate instanceof DoomTelevisionBlockEntity television)) {
                        continue;
                    }
                    double distanceSquared = client.player.distanceToSqr(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5
                    );
                    TelevisionMode mode = television.getBlockState().getValue(DoomTelevisionBlock.MODE);
                    if (best == null || distanceSquared < best.distanceSquared) {
                        best = new LocatedTelevision(pos.immutable(), television.getSessionId(), mode, distanceSquared);
                    }
                }
            }
        }
        return best;
    }

    private java.util.List<Path> findWads() {
        try (Stream<Path> files = Files.list(DoomCraftPaths.WADS)) {
            java.util.List<Path> all = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".wad"))
                    .toList();
            java.util.Set<String> preferredIwads = java.util.Set.of(
                    "doom2.wad", "doom.wad", "doomu.wad", "tnt.wad", "plutonia.wad",
                    "freedoom2.wad", "freedoom1.wad", "heretic.wad", "hexen.wad", "strife1.wad"
            );
            return all.stream().sorted(Comparator
                    .comparingInt((Path path) -> preferredIwads.contains(path.getFileName().toString().toLowerCase()) ? 0 : 1)
                    .thenComparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException exception) {
            DoomCraft.LOGGER.error("Could not enumerate WAD directory", exception);
            return java.util.List.of();
        }
    }

    private void stopSessionNow() {
        pendingStopTicks = -1;
        releaseAllInput();
        if (session != null) {
            session.stopGracefully(1200);
            session = null;
        }
    }

    public void toggleInputFocus(Minecraft client) {
        if (session == null || !session.isAlive() || session.isPaused()) {
            inputFocused = false;
            client.player.sendSystemMessage(Component.translatable("message.doomcraft.controls_unavailable"));
            return;
        }
        inputFocused = !inputFocused;
        if (!inputFocused) {
            releaseAllInput();
        }
        client.player.sendSystemMessage(
                Component.translatable(inputFocused
                        ? "message.doomcraft.controls_enabled"
                        : "message.doomcraft.controls_disabled")
        );
    }

    public boolean isInputFocused() {
        return inputFocused;
    }

    public void setAction(String action, boolean pressed) {
        boolean previous = inputStates.getOrDefault(action, false);
        if (previous == pressed) {
            return;
        }
        inputStates.put(action, pressed);
        if (session != null) {
            session.setAction(action, pressed);
        }
    }

    public void pulseCommand(String command) {
        if (inputFocused && session != null) {
            session.pulseCommand(command);
        }
    }

    public void createManualSave(Minecraft client) {
        if (!manualSaveControlsAvailable(client)) {
            client.player.sendSystemMessage(
                    Component.translatable(
                            "message.doomcraft.manual_save_unavailable"
                    )
            );
            return;
        }

        long timestamp = System.currentTimeMillis();
        String slot = createManualSaveSlot(
                client,
                timestamp
        );

        if (session.createManualSave(slot)) {
            client.player.sendSystemMessage(
                    Component.translatable(
                            "message.doomcraft.manual_save_created",
                            MANUAL_SAVE_TIME_FORMAT.format(
                                    Instant.ofEpochMilli(timestamp)
                            )
                    )
            );
        } else {
            client.player.sendSystemMessage(
                    Component.translatable(
                            "message.doomcraft.manual_save_failed"
                    )
            );
        }
    }

    public void loadLatestManualSave(Minecraft client) {
        if (!manualSaveControlsAvailable(client)) {
            client.player.sendSystemMessage(
                    Component.translatable(
                            "message.doomcraft.manual_load_unavailable"
                    )
            );
            return;
        }

        releaseAllInput();

        if (session.loadLatestManualSave()) {
            client.player.sendSystemMessage(
                    Component.translatable(
                            "message.doomcraft.manual_load_started"
                    )
            );
        } else {
            client.player.sendSystemMessage(
                    Component.translatable(
                            "message.doomcraft.manual_load_missing"
                    )
            );
        }
    }

    private boolean manualSaveControlsAvailable(
            Minecraft client
    ) {
        return inputFocused &&
                client.player != null &&
                session != null &&
                session.isAlive() &&
                !session.isPaused();
    }

    private String createManualSaveSlot(
            Minecraft client,
            long timestamp
    ) {
        String playerId = compactUuid(
                client.player.getUUID()
        );
        String minecraftVersion =
                minecraftVersionToken();
        String worldSeed = worldSeedToken(client);
        String televisionId = compactUuid(
                session.televisionId()
        );

        return String.join(
                "_",
                playerId,
                Long.toString(timestamp),
                minecraftVersion,
                worldSeed,
                televisionId
        );
    }

    private static String compactUuid(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static String minecraftVersionToken() {
        String version = FabricLoader
                .getInstance()
                .getModContainer("minecraft")
                .map(container ->
                        container
                                .getMetadata()
                                .getVersion()
                                .getFriendlyString()
                )
                .orElse("UNKNOWN");

        String digits = version.replaceAll(
                "[^0-9]",
                ""
        );

        return digits.isBlank()
                ? "UNKNOWN"
                : digits;
    }

    private static String worldSeedToken(
            Minecraft client
    ) {
        var server = client.getSingleplayerServer();
        if (server == null) {
            return "NOSEED";
        }

        String seed = Long.toString(
                server.overworld().getSeed()
        );

        return seed.startsWith("-")
                ? "M" + seed.substring(1)
                : seed;
    }

    public void releaseAllInput() {
        if (session != null) {
            inputStates.forEach((action, pressed) -> {
                if (pressed) session.setAction(action, false);
            });
        }
        inputStates.replaceAll((key, value) -> false);
    }

    public Identifier textureFor(BlockPos pos, TelevisionMode mode) {
        if (mode == TelevisionMode.BROKEN) {
            return DoomCraft.id("textures/block/screen_broken.png");
        }
        if (mode == TelevisionMode.OFF) {
            return DoomCraft.id("textures/block/screen_off.png");
        }
        if (mode == TelevisionMode.PAUSED) {
            return DoomCraft.id("textures/block/screen_paused.png");
        }
        if (session != null && session.isAlive() && pos.equals(selectedTelevision)) {
            return DoomDynamicTexture.TEXTURE_ID;
        }
        if (cachedWads.isEmpty()) {
            return DoomCraft.id("textures/block/screen_no_wad.png");
        }
        return DoomCraft.id("textures/block/screen_waiting.png");
    }

    public String lastError() {
        return lastError;
    }

    private record LocatedTelevision(
            BlockPos pos,
            UUID sessionId,
            TelevisionMode mode,
            double distanceSquared
    ) {
    }
}
