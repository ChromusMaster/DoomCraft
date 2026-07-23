package br.com.chromus.doomcraft.client.render;

import br.com.chromus.doomcraft.block.TelevisionMode;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

public final class TelevisionRenderState extends BlockEntityRenderState {
    Direction facing = Direction.NORTH;
    TelevisionMode mode = TelevisionMode.OFF;
    Identifier texture;
}
