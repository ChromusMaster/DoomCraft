package br.com.chromus.doomcraft.registry;

import br.com.chromus.doomcraft.DoomCraft;
import br.com.chromus.doomcraft.block.DoomTelevisionBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final BlockEntityType<DoomTelevisionBlockEntity> TELEVISION = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            DoomCraft.id("television"),
            FabricBlockEntityTypeBuilder.create(
                    DoomTelevisionBlockEntity::new,
                    ModBlocks.televisionArray()
            ).build()
    );

    private ModBlockEntities() {
    }

    public static void initialize() {
    }
}
