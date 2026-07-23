package br.com.chromus.doomcraft.client.render;

import br.com.chromus.doomcraft.block.DoomTelevisionBlock;
import br.com.chromus.doomcraft.block.DoomTelevisionBlockEntity;
import br.com.chromus.doomcraft.client.DoomClientRuntime;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Renderiza somente o framebuffer do LZDoom sobre a superfície frontal
 * da tela CRT. O gabinete permanece sendo renderizado pelo modelo JSON.
 *
 * <p>As coordenadas são informadas diretamente para cada direção do bloco,
 * sem aplicar uma rotação adicional ao PoseStack. Isso mantém o framebuffer
 * exatamente alinhado ao mesmo lado frontal usado pelo blockstate/modelo.</p>
 */
public final class TelevisionBlockEntityRenderer
        implements BlockEntityRenderer<DoomTelevisionBlockEntity, TelevisionRenderState> {

    /*
     * Modelo-base voltado para NORTH:
     *
     * - painel ampliado: X -6..15.5, Y 4..21, Z 0.8..3.2;
     * - a imagem ocupa cerca de 1,21 × 0,90 bloco;
     * - SCREEN_FRONT fica ligeiramente à frente do vidro,
     *   evitando z-fighting e impedindo que o painel preto oculte o frame.
     */
    private static final float SCREEN_MIN_X = -5.20f / 16.0f;
    private static final float SCREEN_MAX_X = 14.20f / 16.0f;
    private static final float SCREEN_MIN_Y = 5.30f / 16.0f;
    private static final float SCREEN_MAX_Y = 19.70f / 16.0f;
    private static final float SCREEN_FRONT = 0.65f / 16.0f;

    /*
     * Valor de iluminação máxima usado pelo Minecraft.
     * A tela representa um monitor emissivo e não deve escurecer à noite.
     */
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final int WHITE = 0xFFFFFFFF;

    public TelevisionBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public TelevisionRenderState createRenderState() {
        return new TelevisionRenderState();
    }

    @Override
    public void extractRenderState(
            DoomTelevisionBlockEntity blockEntity,
            TelevisionRenderState state,
            float tickProgress,
            Vec3 cameraPos,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
    ) {
        BlockEntityRenderer.super.extractRenderState(
                blockEntity,
                state,
                tickProgress,
                cameraPos,
                crumblingOverlay
        );

        state.facing = blockEntity
                .getBlockState()
                .getValue(DoomTelevisionBlock.FACING);

        state.mode = blockEntity
                .getBlockState()
                .getValue(DoomTelevisionBlock.MODE);

        state.texture = DoomClientRuntime
                .getInstance()
                .textureFor(blockEntity.getBlockPos(), state.mode);
    }

    @Override
    public void submit(
            TelevisionRenderState state,
            PoseStack matrices,
            SubmitNodeCollector queue,
            CameraRenderState cameraState
    ) {
        if (state.texture == null || state.facing == null) {
            return;
        }

        matrices.pushPose();

        queue.submitCustomGeometry(
                matrices,
                RenderTypes.entityCutout(state.texture),
                (pose, vertices) -> submitScreen(
                        state.facing,
                        pose,
                        vertices
                )
        );

        matrices.popPose();
    }

    /**
     * Posiciona o plano diretamente sobre a frente física do modelo em cada
     * orientação. Não há transformação de rotação adicional; portanto, não
     * existe diferença de convenção entre a direção, o PoseStack e os valores
     * de rotação do arquivo blockstate.
     */
    private static void submitScreen(
            Direction facing,
            PoseStack.Pose pose,
            VertexConsumer vertices
    ) {
        float inverseMinX = 1.0f - SCREEN_MAX_X;
        float inverseMaxX = 1.0f - SCREEN_MIN_X;
        float inverseFront = 1.0f - SCREEN_FRONT;

        switch (facing) {
            /*
             * Frente NORTH: Z mínimo.
             *
             * Para quem observa pelo norte olhando para o sul, o lado esquerdo
             * visual corresponde ao X maior. Esta ordem mantém textos e menus
             * do Doom legíveis, sem espelhamento.
             */
            case NORTH -> submitQuad(
                    pose,
                    vertices,
                    SCREEN_MAX_X, SCREEN_MAX_Y, SCREEN_FRONT,
                    SCREEN_MIN_X, SCREEN_MAX_Y, SCREEN_FRONT,
                    SCREEN_MIN_X, SCREEN_MIN_Y, SCREEN_FRONT,
                    SCREEN_MAX_X, SCREEN_MIN_Y, SCREEN_FRONT,
                    0.0f, 0.0f, -1.0f
            );

            /*
             * Frente EAST: X máximo. A faixa horizontal da tela passa a ocupar
             * Z = SCREEN_MIN_X..SCREEN_MAX_X.
             */
            case EAST -> submitQuad(
                    pose,
                    vertices,
                    inverseFront, SCREEN_MAX_Y, SCREEN_MAX_X,
                    inverseFront, SCREEN_MAX_Y, SCREEN_MIN_X,
                    inverseFront, SCREEN_MIN_Y, SCREEN_MIN_X,
                    inverseFront, SCREEN_MIN_Y, SCREEN_MAX_X,
                    1.0f, 0.0f, 0.0f
            );

            /*
             * Frente SOUTH: Z máximo. O deslocamento assimétrico do painel é
             * invertido junto com o modelo, preservando o espaço dos botões.
             */
            case SOUTH -> submitQuad(
                    pose,
                    vertices,
                    inverseMinX, SCREEN_MAX_Y, inverseFront,
                    inverseMaxX, SCREEN_MAX_Y, inverseFront,
                    inverseMaxX, SCREEN_MIN_Y, inverseFront,
                    inverseMinX, SCREEN_MIN_Y, inverseFront,
                    0.0f, 0.0f, 1.0f
            );

            /*
             * Frente WEST: X mínimo. A faixa horizontal também é invertida
             * para acompanhar exatamente a rotação do modelo-base.
             */
            case WEST -> submitQuad(
                    pose,
                    vertices,
                    SCREEN_FRONT, SCREEN_MAX_Y, inverseMinX,
                    SCREEN_FRONT, SCREEN_MAX_Y, inverseMaxX,
                    SCREEN_FRONT, SCREEN_MIN_Y, inverseMaxX,
                    SCREEN_FRONT, SCREEN_MIN_Y, inverseMinX,
                    -1.0f, 0.0f, 0.0f
            );

            default -> {
                // FACING é horizontal, mas mantemos um default defensivo.
            }
        }
    }

    /**
     * Envia os quatro vértices em ordem:
     * superior esquerdo, superior direito, inferior direito e inferior esquerdo.
     */
    private static void submitQuad(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float topLeftX,
            float topLeftY,
            float topLeftZ,
            float topRightX,
            float topRightY,
            float topRightZ,
            float bottomRightX,
            float bottomRightY,
            float bottomRightZ,
            float bottomLeftX,
            float bottomLeftY,
            float bottomLeftZ,
            float normalX,
            float normalY,
            float normalZ
    ) {
        submitVertex(
                pose,
                vertices,
                topLeftX,
                topLeftY,
                topLeftZ,
                0.0f,
                0.0f,
                normalX,
                normalY,
                normalZ
        );

        submitVertex(
                pose,
                vertices,
                topRightX,
                topRightY,
                topRightZ,
                1.0f,
                0.0f,
                normalX,
                normalY,
                normalZ
        );

        submitVertex(
                pose,
                vertices,
                bottomRightX,
                bottomRightY,
                bottomRightZ,
                1.0f,
                1.0f,
                normalX,
                normalY,
                normalZ
        );

        submitVertex(
                pose,
                vertices,
                bottomLeftX,
                bottomLeftY,
                bottomLeftZ,
                0.0f,
                1.0f,
                normalX,
                normalY,
                normalZ
        );
    }

    private static void submitVertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float x,
            float y,
            float z,
            float u,
            float v,
            float normalX,
            float normalY,
            float normalZ
    ) {
        vertices.addVertex(pose, x, y, z)
                .setColor(WHITE)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT_LIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
