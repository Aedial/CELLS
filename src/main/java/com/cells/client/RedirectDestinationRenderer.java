package com.cells.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cells.items.upgrades.ItemRedirectUnselectedCard;


@SideOnly(Side.CLIENT)
public class RedirectDestinationRenderer {

    private static final double MAX_DISTANCE_SQUARED = 32 * 32;

    @SubscribeEvent
    public void renderDestination(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null) return;

        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) return;

        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.getPartialTicks();
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.getPartialTicks();
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.getPartialTicks();

        for (EnumHand hand : EnumHand.values()) {
            ItemStack stack = minecraft.player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemRedirectUnselectedCard)) continue;

            BlockPos pos = ItemRedirectUnselectedCard.getDestination(stack);
            if (pos == null || ItemRedirectUnselectedCard.getDimension(stack)
                    != minecraft.world.provider.getDimension()) continue;
            if (minecraft.player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > MAX_DISTANCE_SQUARED) continue;

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.glLineWidth(2.0F);
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(pos).grow(0.002).offset(-x, -y, -z),
                    0.0F, 1.0F, 0.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
        }
    }
}
