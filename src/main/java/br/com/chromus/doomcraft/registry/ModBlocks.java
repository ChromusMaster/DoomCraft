package br.com.chromus.doomcraft.registry;

import br.com.chromus.doomcraft.DoomCraft;
import br.com.chromus.doomcraft.block.DoomTelevisionBlock;
import br.com.chromus.doomcraft.block.VanillaWoodVariant;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    private static final EnumMap<VanillaWoodVariant, DoomTelevisionBlock> TELEVISIONS =
            new EnumMap<>(VanillaWoodVariant.class);

    static {
        for (VanillaWoodVariant variant : VanillaWoodVariant.values()) {
            String path = variant == VanillaWoodVariant.OAK
                    ? "television"
                    : variant.id() + "_television";
            TELEVISIONS.put(variant, registerTelevision(path, variant));
        }
    }

    public static final DoomTelevisionBlock TELEVISION = TELEVISIONS.get(VanillaWoodVariant.OAK);

    private ModBlocks() {
    }

    private static DoomTelevisionBlock registerTelevision(String path, VanillaWoodVariant variant) {
        Identifier identifier = DoomCraft.id(path);
        BlockItemId id = BlockItemId.create(identifier, identifier);

        DoomTelevisionBlock block = new DoomTelevisionBlock(
                variant,
                BlockBehaviour.Properties.of()
                        .strength(2.5F, 4.0F)
                        .sound(SoundType.WOOD)
                        .noOcclusion()
                        .setId(id.block())
        );
        Registry.register(BuiltInRegistries.BLOCK, id.block(), block);

        BlockItem item = new BlockItem(
                block,
                new Item.Properties()
                        .useBlockDescriptionPrefix()
                        .setId(id.item())
        );
        Registry.register(BuiltInRegistries.ITEM, id.item(), item);
        return block;
    }

    public static DoomTelevisionBlock television(VanillaWoodVariant variant) {
        return TELEVISIONS.getOrDefault(variant, TELEVISION);
    }

    public static Map<VanillaWoodVariant, DoomTelevisionBlock> televisions() {
        return Collections.unmodifiableMap(TELEVISIONS);
    }

    public static Block[] televisionArray() {
        return TELEVISIONS.values().toArray(Block[]::new);
    }

    public static void initialize() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
                .register(entries -> TELEVISIONS.values().forEach(entries::accept));
    }
}
