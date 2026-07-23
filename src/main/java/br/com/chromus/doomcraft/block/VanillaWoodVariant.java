package br.com.chromus.doomcraft.block;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

public enum VanillaWoodVariant {
    OAK("oak", Items.OAK_PLANKS),
    SPRUCE("spruce", Items.SPRUCE_PLANKS),
    BIRCH("birch", Items.BIRCH_PLANKS),
    JUNGLE("jungle", Items.JUNGLE_PLANKS),
    ACACIA("acacia", Items.ACACIA_PLANKS),
    DARK_OAK("dark_oak", Items.DARK_OAK_PLANKS),
    MANGROVE("mangrove", Items.MANGROVE_PLANKS),
    CHERRY("cherry", Items.CHERRY_PLANKS),
    BAMBOO("bamboo", Items.BAMBOO_PLANKS),
    CRIMSON("crimson", Items.CRIMSON_PLANKS),
    WARPED("warped", Items.WARPED_PLANKS),
    PALE_OAK("pale_oak", Items.PALE_OAK_PLANKS);

    private static final Map<Item, VanillaWoodVariant> BY_PLANK = new HashMap<>();

    static {
        for (VanillaWoodVariant value : values()) {
            BY_PLANK.put(value.plank, value);
        }
    }

    private final String id;
    private final Item plank;

    VanillaWoodVariant(String id, Item plank) {
        this.id = id;
        this.plank = plank;
    }

    public String id() {
        return id;
    }

    public Item plank() {
        return plank;
    }

    public static VanillaWoodVariant fromPlank(Item item) {
        return BY_PLANK.getOrDefault(item, OAK);
    }

    public static boolean isRecognized(Item item) {
        return BY_PLANK.containsKey(item);
    }
}
