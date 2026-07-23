package br.com.chromus.doomcraft;

import br.com.chromus.doomcraft.config.DoomCraftPaths;
import br.com.chromus.doomcraft.recipe.ModRecipes;
import br.com.chromus.doomcraft.registry.ModBlockEntities;
import br.com.chromus.doomcraft.registry.ModBlocks;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DoomCraft implements ModInitializer {
    public static final String MOD_ID = "doomcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        DoomCraftPaths.ensureDirectories();
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModRecipes.initialize();
        LOGGER.info("DoomCraft 1.0 initialized. WAD directory: {}", DoomCraftPaths.WADS);
    }
}
