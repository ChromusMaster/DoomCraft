package br.com.chromus.doomcraft.recipe;

import br.com.chromus.doomcraft.DoomCraft;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModRecipes {
    private ModRecipes() {
    }

    public static void initialize() {
        Registry.register(
                BuiltInRegistries.RECIPE_SERIALIZER,
                DoomCraft.id("television"),
                DoomTelevisionRecipe.SERIALIZER
        );
        RecipeSynchronization.synchronizeRecipeSerializer(DoomTelevisionRecipe.SERIALIZER);
    }
}
