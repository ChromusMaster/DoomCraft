package br.com.chromus.doomcraft.client;

import br.com.chromus.doomcraft.client.input.DoomKeyMappings;
import br.com.chromus.doomcraft.client.render.TelevisionBlockEntityRenderer;
import br.com.chromus.doomcraft.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class DoomCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DoomClientRuntime runtime = DoomClientRuntime.getInstance();
        runtime.initialize();
        DoomKeyMappings.initialize(runtime);

        BlockEntityRenderers.register(
                ModBlockEntities.TELEVISION,
                TelevisionBlockEntityRenderer::new
        );

        ClientTickEvents.END_CLIENT_TICK.register(runtime::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> runtime.onWorldJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> runtime.onWorldLeave());
    }
}
