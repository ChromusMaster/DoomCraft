package br.com.chromus.doomcraft.block;

import br.com.chromus.doomcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class DoomTelevisionBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    public static final EnumProperty<TelevisionMode> MODE =
            EnumProperty.create(
                    "mode",
                    TelevisionMode.class
            );

    /*
     * A TV continua sendo um único bloco lógico/BlockEntity, mas o modelo e
     * a seleção física ocupam aproximadamente 2 blocos de largura por
     * 1,44 bloco de altura.
     */
    private static final VoxelShape SHAPE_NORTH_SOUTH =
            Block.box(
                    -12.0,
                    0.0,
                    0.5,
                    28.0,
                    25.5,
                    15.5
            );

    private static final VoxelShape SHAPE_EAST_WEST =
            Block.box(
                    0.5,
                    0.0,
                    -12.0,
                    15.5,
                    25.5,
                    28.0
            );

    private final VanillaWoodVariant woodVariant;

    public DoomTelevisionBlock(
            VanillaWoodVariant woodVariant,
            Properties properties
    ) {
        super(properties);
        this.woodVariant = woodVariant;

        registerDefaultState(
                stateDefinition
                        .any()
                        .setValue(
                                FACING,
                                Direction.NORTH
                        )
                        .setValue(
                                MODE,
                                TelevisionMode.ACTIVE
                        )
        );
    }

    public VanillaWoodVariant woodVariant() {
        return woodVariant;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(
            BlockPlaceContext context
    ) {
        return defaultBlockState()
                .setValue(
                        FACING,
                        context
                                .getHorizontalDirection()
                                .getOpposite()
                )
                .setValue(
                        MODE,
                        TelevisionMode.ACTIVE
                );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING, MODE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return state.getValue(FACING).getAxis() ==
                Direction.Axis.X
                ? SHAPE_EAST_WEST
                : SHAPE_NORTH_SOUTH;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (player.isShiftKeyDown()) {
            if (
                !level.isClientSide() &&
                level.getBlockEntity(pos)
                        instanceof DoomTelevisionBlockEntity television
            ) {
                television.setBroken(
                        state.getValue(MODE) !=
                                TelevisionMode.BROKEN
                );
            }

            return InteractionResult.SUCCESS;
        }

        if (level.isClientSide()) {
            player.sendSystemMessage(
                    Component.translatable(
                            "message.doomcraft.interact_hint"
                    )
            );
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(
            BlockPos pos,
            BlockState state
    ) {
        return new DoomTelevisionBlockEntity(
                pos,
                state
        );
    }

    @Nullable
    @Override
    public <T extends BlockEntity>
    BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return createTickerHelper(
                type,
                ModBlockEntities.TELEVISION,
                DoomTelevisionBlockEntity::tick
        );
    }
}
