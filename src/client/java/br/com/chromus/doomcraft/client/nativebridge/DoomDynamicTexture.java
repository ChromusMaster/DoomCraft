package br.com.chromus.doomcraft.client.nativebridge;

import br.com.chromus.doomcraft.DoomCraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class DoomDynamicTexture implements AutoCloseable {
    public static final int WIDTH = 640;
    public static final int HEIGHT = 400;
    public static final Identifier TEXTURE_ID = DoomCraft.id("doom_framebuffer");

    private NativeImage image;
    private DynamicTexture texture;

    public void register() {
        if (texture != null) {
            return;
        }
        image = new NativeImage(WIDTH, HEIGHT, false);
        texture = new DynamicTexture(() -> "DoomCraft framebuffer", image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, texture);
        fill(0xFF071018);
    }

    public void upload(byte[] rgba) {
        if (image == null || texture == null) {
            return;
        }
        int expected = WIDTH * HEIGHT * 4;
        if (rgba.length < expected) {
            return;
        }

        int index = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int red = rgba[index++] & 0xFF;
                int green = rgba[index++] & 0xFF;
                int blue = rgba[index++] & 0xFF;
                int alpha = rgba[index++] & 0xFF;
                int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;
                image.setPixelABGR(x, y, abgr);
            }
        }
        texture.upload();
    }

    public void fill(int abgr) {
        if (image == null || texture == null) {
            return;
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setPixelABGR(x, y, abgr);
            }
        }
        texture.upload();
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
            image = null;
        }
    }
}
