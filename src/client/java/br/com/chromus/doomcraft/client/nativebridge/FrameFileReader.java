package br.com.chromus.doomcraft.client.nativebridge;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class FrameFileReader {
    private static final int MAGIC = 0x31464344; // "DCF1" in little-endian.
    private static final int HEADER_SIZE = 32;
    private long lastSequence = -1L;

    public Optional<byte[]> read(Path frameFile) {
        if (!Files.isRegularFile(frameFile)) {
            return Optional.empty();
        }

        try (FileChannel channel = FileChannel.open(frameFile, StandardOpenOption.READ)) {
            long size = channel.size();
            if (size < HEADER_SIZE) {
                return Optional.empty();
            }

            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            if (channel.read(header) != HEADER_SIZE) {
                return Optional.empty();
            }
            header.flip();

            int magic = header.getInt();
            int version = header.getInt();
            int width = header.getInt();
            int height = header.getInt();
            long sequence = header.getLong();
            int format = header.getInt();
            int dataLength = header.getInt();

            if (magic != MAGIC || version != 1 || format != 1) {
                return Optional.empty();
            }
            if (width != DoomDynamicTexture.WIDTH || height != DoomDynamicTexture.HEIGHT) {
                return Optional.empty();
            }
            if (sequence == lastSequence || dataLength != width * height * 4) {
                return Optional.empty();
            }
            if (size < HEADER_SIZE + dataLength) {
                return Optional.empty();
            }

            ByteBuffer pixels = ByteBuffer.allocate(dataLength);
            int total = 0;
            while (pixels.hasRemaining()) {
                int read = channel.read(pixels);
                if (read < 0) {
                    break;
                }
                total += read;
            }
            if (total != dataLength) {
                return Optional.empty();
            }

            lastSequence = sequence;
            return Optional.of(pixels.array());
        } catch (IOException ignored) {
            // The native side atomically replaces the frame file. A transient read failure is expected.
            return Optional.empty();
        }
    }
}
