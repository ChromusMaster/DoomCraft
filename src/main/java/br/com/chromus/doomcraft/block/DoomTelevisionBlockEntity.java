package br.com.chromus.doomcraft.block;

import br.com.chromus.doomcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

public final class DoomTelevisionBlockEntity extends BlockEntity {
    private UUID sessionId = UUID.randomUUID();
    private long transitionSerial;
    private int tickDivider;

    public DoomTelevisionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEVISION, pos, state);
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public long getTransitionSerial() {
        return transitionSerial;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DoomTelevisionBlockEntity entity) {
        if (level.isClientSide()) {
            return;
        }

        if (++entity.tickDivider < 10) {
            return;
        }
        entity.tickDivider = 0;

        TelevisionMode current = state.getValue(DoomTelevisionBlock.MODE);
        if (current == TelevisionMode.BROKEN) {
            return;
        }

        Player nearest = level.getNearestPlayer(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                -1.0,
                false
        );

        double distanceSquared = nearest == null
                ? Double.POSITIVE_INFINITY
                : nearest.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        TelevisionMode desired;
        if (distanceSquared <= 16.0) {
            desired = TelevisionMode.ACTIVE;
        } else if (distanceSquared <= 64.0) {
            desired = TelevisionMode.PAUSED;
        } else {
            desired = TelevisionMode.OFF;
        }

        if (desired != current) {
            entity.transitionSerial++;
            level.setBlock(pos, state.setValue(DoomTelevisionBlock.MODE, desired), Block.UPDATE_ALL);
            entity.setChanged();
        }
    }

    public void setBroken(boolean broken) {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        TelevisionMode next = broken ? TelevisionMode.BROKEN : TelevisionMode.ACTIVE;
        transitionSerial++;
        level.setBlock(worldPosition, state.setValue(DoomTelevisionBlock.MODE, next), Block.UPDATE_ALL);
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        output.putString("session_id", sessionId.toString());
        output.putLong("transition_serial", transitionSerial);
        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String storedId = input.getStringOr("session_id", "");
        try {
            sessionId = storedId.isBlank() ? UUID.randomUUID() : UUID.fromString(storedId);
        } catch (IllegalArgumentException ignored) {
            sessionId = UUID.randomUUID();
        }
        transitionSerial = input.getLongOr("transition_serial", 0L);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
        }
    }
}
