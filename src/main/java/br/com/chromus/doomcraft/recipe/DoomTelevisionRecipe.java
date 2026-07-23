package br.com.chromus.doomcraft.recipe;

import br.com.chromus.doomcraft.block.VanillaWoodVariant;
import br.com.chromus.doomcraft.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class DoomTelevisionRecipe extends CustomRecipe {
    public static final DoomTelevisionRecipe INSTANCE = new DoomTelevisionRecipe();
    public static final MapCodec<DoomTelevisionRecipe> CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, DoomTelevisionRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<DoomTelevisionRecipe> SERIALIZER =
            new RecipeSerializer<>(CODEC, STREAM_CODEC);

    private DoomTelevisionRecipe() {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return validLayout(input);
    }

    private static boolean validLayout(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }

        return is(input, 0, Items.IRON_INGOT)
                && is(input, 1, Items.IRON_INGOT)
                && is(input, 2, Items.IRON_INGOT)
                && is(input, 3, Items.GLASS_PANE)
                && is(input, 4, Items.REDSTONE)
                && is(input, 5, Items.GLASS_PANE)
                && input.getItem(6).is(ItemTags.PLANKS)
                && input.getItem(7).is(ItemTags.PLANKS)
                && input.getItem(8).is(ItemTags.PLANKS);
    }

    private static boolean is(CraftingInput input, int slot, Item item) {
        return input.getItem(slot).is(item);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        if (!validLayout(input)) {
            return ItemStack.EMPTY;
        }

        Item first = input.getItem(6).getItem();
        Item second = input.getItem(7).getItem();
        Item third = input.getItem(8).getItem();

        VanillaWoodVariant variant = VanillaWoodVariant.OAK;
        if (first == second && second == third && VanillaWoodVariant.isRecognized(first)) {
            variant = VanillaWoodVariant.fromPlank(first);
        }

        // Mixed vanilla planks and every modded plank intentionally fall back to oak/default.
        return new ItemStack(ModBlocks.television(variant));
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SERIALIZER;
    }
}
