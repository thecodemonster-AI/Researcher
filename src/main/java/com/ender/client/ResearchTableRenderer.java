package com.ender.client;

import com.ender.blockentity.ResearchTableBlockEntity;
import com.ender.blockentity.ResearchTableBlockEntity.HologramRenderData;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;

public class ResearchTableRenderer implements BlockEntityRenderer<ResearchTableBlockEntity> {

    public ResearchTableRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(ResearchTableBlockEntity be, float partialTicks, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        HologramRenderData data = be.getHologramRenderData(partialTicks);
        if (data == null) return;

        pose.pushPose();
        double baseY = 1.2D;
        float time = be.getLevel() == null ? 0F : (be.getLevel().getGameTime() + partialTicks);
        float bob = (float) Math.sin(time / 10.0F) * 0.05F;
        pose.translate(0.5D, baseY + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees((time * 4.0F) % 360F));
        float scale = 0.7F;
        pose.scale(scale, scale, scale);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, data.alpha());
        Minecraft.getInstance().getItemRenderer().renderStatic(
                data.stack(),
                ItemDisplayContext.GROUND,
                0xF000F0,
                overlay,
                pose,
                buffers,
                be.getLevel(),
                0
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        pose.popPose();
    }
}