package com.emowolf.scm.client;

import com.emowolf.scm.blockentity.EternalBeaconBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class EternalBeaconRenderer implements BlockEntityRenderer<EternalBeaconBlockEntity> {

    public EternalBeaconRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(EternalBeaconBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (blockEntity.getChocolateEnergy() > 0) {
            BeamRenderUtil.renderBeam(
                    poseStack, bufferSource,
                    partialTick,
                    blockEntity.getLevel().getGameTime(),
                    1024,
                    BeamRenderUtil.COLOR_GOLDEN
            );
        }
    }

    @Override
    public boolean shouldRenderOffScreen(EternalBeaconBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
