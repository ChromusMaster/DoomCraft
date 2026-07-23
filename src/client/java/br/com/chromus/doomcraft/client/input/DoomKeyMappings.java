package br.com.chromus.doomcraft.client.input;

import br.com.chromus.doomcraft.DoomCraft;
import br.com.chromus.doomcraft.client.DoomClientRuntime;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DoomKeyMappings {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(DoomCraft.id("controls"));
    private static final Map<String, KeyMapping> HELD_ACTIONS = new LinkedHashMap<>();

    private static KeyMapping toggleFocus;
    private static KeyMapping previousWeapon;
    private static KeyMapping nextWeapon;

    private DoomKeyMappings() {
    }

    public static void initialize(DoomClientRuntime runtime) {
        toggleFocus = register("key.doomcraft.focus", GLFW.GLFW_KEY_F8);
        HELD_ACTIONS.put("forward", register("key.doomcraft.forward", GLFW.GLFW_KEY_UP));
        HELD_ACTIONS.put("back", register("key.doomcraft.back", GLFW.GLFW_KEY_DOWN));
        HELD_ACTIONS.put("left", register("key.doomcraft.left", GLFW.GLFW_KEY_LEFT));
        HELD_ACTIONS.put("right", register("key.doomcraft.right", GLFW.GLFW_KEY_RIGHT));
        HELD_ACTIONS.put("attack", register("key.doomcraft.attack", GLFW.GLFW_KEY_RIGHT_CONTROL));
        HELD_ACTIONS.put("use", register("key.doomcraft.use", GLFW.GLFW_KEY_ENTER));
        HELD_ACTIONS.put("speed", register("key.doomcraft.run", GLFW.GLFW_KEY_RIGHT_SHIFT));
        previousWeapon = register("key.doomcraft.weapon_previous", GLFW.GLFW_KEY_PAGE_UP);
        nextWeapon = register("key.doomcraft.weapon_next", GLFW.GLFW_KEY_PAGE_DOWN);

        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client, runtime));
    }

    private static KeyMapping register(String translationKey, int glfwKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey,
                InputConstants.Type.KEYSYM,
                glfwKey,
                CATEGORY
        ));
    }

    private static void tick(Minecraft client, DoomClientRuntime runtime) {
        while (toggleFocus.consumeClick()) {
            runtime.toggleInputFocus(client);
        }

        if (!runtime.isInputFocused()) {
            runtime.releaseAllInput();
            return;
        }

        HELD_ACTIONS.forEach((action, key) -> runtime.setAction(action, key.isDown()));

        while (previousWeapon.consumeClick()) {
            runtime.pulseCommand("weapprev");
        }
        while (nextWeapon.consumeClick()) {
            runtime.pulseCommand("weapnext");
        }
    }
}
