package com.emowolf.scm.client.model;

import com.emowolf.scm.item.MixedChocolateItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 混合巧克力的动态缩放 BakedModel。
 * 根据 ItemStack NBT 中的 mix_level 在所有显示上下文中放大模型
 *（手持、地面、展示框、GUI 等）。
 * <p>
 * 性能说明：scale 仅有 ~20 种可能值，通过 ConcurrentHashMap 缓存实例。
 */
public class MixedChocolateBakedModel implements BakedModel {

    private final BakedModel parent;
    private final ItemOverrides itemOverrides;

    private static final ConcurrentHashMap<Float, ScaledDelegateModel> SCALE_CACHE = new ConcurrentHashMap<>();

    public MixedChocolateBakedModel(BakedModel parent) {
        this.parent = parent;
        this.itemOverrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack,
                                      @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                float scale = MixedChocolateItem.getScale(stack);
                if (scale == 1.0f) {
                    return parent;
                }
                return SCALE_CACHE.computeIfAbsent(scale, s -> new ScaledDelegateModel(parent, s));
            }
        };
    }

    @Override
    public ItemOverrides getOverrides() {
        return itemOverrides;
    }

    // ==== Delegates to parent ====

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return parent.getQuads(state, side, rand);
    }

    @Override
    public boolean useAmbientOcclusion() {
        return parent.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return parent.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return parent.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return parent.getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return parent.getTransforms();
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHanded) {
        return parent.applyTransform(context, poseStack, leftHanded);
    }

    /**
     * 缩放版委托模型，在所有显示上下文中多乘一个 scale。
     */
    private static class ScaledDelegateModel implements BakedModel {
        private final BakedModel parent;
        private final float scale;
        private final ItemTransforms scaledTransforms;

        ScaledDelegateModel(BakedModel parent, float scale) {
            this.parent = parent;
            this.scale = scale;
            this.scaledTransforms = buildScaledTransforms(parent.getTransforms(), scale);
        }

        /** 所有显示上下文统一缩放 */
        private static ItemTransforms buildScaledTransforms(ItemTransforms original, float scale) {
            return new ItemTransforms(
                    scaleTransform(original.thirdPersonLeftHand, scale),
                    scaleTransform(original.thirdPersonRightHand, scale),
                    scaleTransform(original.firstPersonLeftHand, scale),
                    scaleTransform(original.firstPersonRightHand, scale),
                    scaleTransform(original.head, scale),
                    scaleTransform(original.gui, scale),
                    scaleTransform(original.ground, scale),
                    scaleTransform(original.fixed, scale)
            );
        }

        private static ItemTransform scaleTransform(ItemTransform original, float scale) {
            Vector3f s = original.scale;
            return new ItemTransform(
                    original.rotation,
                    original.translation,
                    new Vector3f(s.x() * scale, s.y() * scale, s.z() * scale)
            );
        }

        @Override
        public ItemTransforms getTransforms() {
            return scaledTransforms;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHanded) {
            parent.applyTransform(context, poseStack, leftHanded);
            poseStack.scale(scale, scale, scale);
            return this;
        }

        // ==== Delegates to parent ====

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return parent.getQuads(state, side, rand);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return parent.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return parent.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return parent.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return parent.getParticleIcon();
        }

        @Override
        public ItemOverrides getOverrides() {
            return parent.getOverrides();
        }
    }
}
