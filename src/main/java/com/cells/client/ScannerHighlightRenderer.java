package com.cells.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.cells.gui.overlay.MessageHelper;


/**
 * Renders block highlight outlines that are visible through walls.
 * For distant blocks (>50 blocks), renders directional arrows instead.
 */
public class ScannerHighlightRenderer {

    private static final Map<BlockPos, Long> highlightedBlocks = new HashMap<>();
    private static int highlightDimension;

    // Distance threshold: wireframe under 50 blocks, arrow over 50 blocks
    private static final double WIREFRAME_MAX_DISTANCE = 50.0;

    // Highlight duration for standard highlights (15 seconds)
    private static final long HIGHLIGHT_DURATION_MS = 15000L;

    // Arrow rendering constants (adapted from ScannerRenderer)
    private static final float ARROW_BASE_DISTANCE = 0.6f;
    private static final float ARROW_SPREAD_RADIUS = 0.12f;
    private static final float ARROW_LENGTH = 0.05f;
    private static final float ARROW_WIDTH = 0.02f;
    private static final float ARROW_THICKNESS = 0.01f;
    private static final float MIN_PITCH_ANGLE = 10.0f;
    private static final float BASE_TEXT_SCALE = 0.0012f;
    private static final float BASE_TEXT_HEIGHT_OFFSET = 0.02f;
    private static final float ARROW_ALPHA = 1.0f;

    // Arrow gradient constants
    private static final float GRADIENT_START_FACTOR = 0.8f;
    private static final float GRADIENT_END_FACTOR = 0.4f;
    private static final float GRADIENT_CURVE = 0.5f;
    private static final int GRADIENT_RINGS = 16;

    // Highlight color (green)
    private static final int HIGHLIGHT_COLOR = 0x00FF80;

    public static void highlight(BlockPos pos, int dimension) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;
        if (player.dimension != dimension) {
            MessageHelper.error("cells.interface_scanner.different_dimension");
            return;
        }

        highlightDimension = dimension;
        addHighlight(pos, HIGHLIGHT_DURATION_MS);
        MessageHelper.success("cells.interface_scanner.highlighted", pos.getX(), pos.getY(), pos.getZ());
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) clearHighlights();
    }

    /**
     * Add a block to be highlighted for the specified duration.
     * @param pos Block position to highlight
     * @param durationMs Duration in milliseconds
     */
    public static void addHighlight(BlockPos pos, long durationMs) {
        highlightedBlocks.put(pos, System.currentTimeMillis() + durationMs);
    }

    /**
     * Remove a highlight for a specific block.
     */
    public static void removeHighlight(BlockPos pos) {
        highlightedBlocks.remove(pos);
    }

    /**
     * Clear all highlights.
     */
    public static void clearHighlights() {
        highlightedBlocks.clear();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (highlightedBlocks.isEmpty()) return;

        long now = System.currentTimeMillis();

        // Remove expired standard highlights
        highlightedBlocks.entrySet().removeIf(blockPosLongEntry -> blockPosLongEntry.getValue() < now);

        if (highlightedBlocks.isEmpty()) return;

        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null || player.dimension != highlightDimension) {
            clearHighlights();
            return;
        }

        BlockPos playerPos = player.getPosition();
        float partialTicks = event.getPartialTicks();

        // Split highlights into close (wireframe) and distant (arrow)
        List<BlockPos> closeHighlights = new ArrayList<>();
        List<BlockPos> distantHighlights = new ArrayList<>();

        double wireframeDistSq = WIREFRAME_MAX_DISTANCE * WIREFRAME_MAX_DISTANCE;

        for (BlockPos pos : highlightedBlocks.keySet()) {
            double distSq = pos.distanceSq(playerPos);
            if (distSq <= wireframeDistSq) {
                closeHighlights.add(pos);
            } else {
                distantHighlights.add(pos);
            }
        }

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        // Render close highlights as wireframes
        if (!closeHighlights.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(-playerX, -playerY, -playerZ);

            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.glLineWidth(3.0F);

            for (BlockPos pos : closeHighlights) {
                float alpha = 0.7f + 0.3f * (float) Math.sin(now / 500.0);
                renderBlockOutline(pos, 0.0f, 1.0f, 0.5f, alpha);
            }

            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }

        // Render distant highlights as directional arrows
        for (BlockPos pos : distantHighlights) {
            double distance = Math.sqrt(pos.distanceSq(playerPos));
            drawDirectionArrow(player, pos, HIGHLIGHT_COLOR, distance, partialTicks);
        }
    }

    private void renderBlockOutline(BlockPos pos, float red, float green, float blue, float alpha) {
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.002);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Bottom face
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        buffer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);

        // Top face
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();

        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        // Vertical edges
        buffer.pos(box.minX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.minZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.minZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.maxX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.maxX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        buffer.pos(box.minX, box.minY, box.maxZ).color(red, green, blue, alpha).endVertex();
        buffer.pos(box.minX, box.maxY, box.maxZ).color(red, green, blue, alpha).endVertex();

        tessellator.draw();
    }

    /**
     * Draw a 3D directional arrow pointing towards the target position.
     * Adapted from ScannerRenderer in AE2-PowerTools.
     */
    private void drawDirectionArrow(EntityPlayer player, BlockPos target, int color,
            double distance, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;
        double eyeY = playerY + player.getEyeHeight();

        float cameraYaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float cameraPitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;

        double dx = target.getX() + 0.5 - playerX;
        double dy = target.getY() + 0.5 - eyeY;
        double dz = target.getZ() + 0.5 - playerZ;
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDist < 1) return;

        float targetYaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        float targetPitch;
        float rawPitch = (float) Math.toDegrees(Math.atan2(-dy, horizontalDist));
        if (rawPitch > -MIN_PITCH_ANGLE && rawPitch < MIN_PITCH_ANGLE) {
            targetPitch = rawPitch > 0 ? MIN_PITCH_ANGLE : -MIN_PITCH_ANGLE;
        } else {
            targetPitch = rawPitch;
        }

        double camYawRad = Math.toRadians(cameraYaw);
        double camPitchRad = Math.toRadians(cameraPitch);
        double camForwardX = -Math.sin(camYawRad) * Math.cos(camPitchRad);
        double camForwardY = -Math.sin(camPitchRad);
        double camForwardZ = Math.cos(camYawRad) * Math.cos(camPitchRad);

        double baseX = playerX + camForwardX * ARROW_BASE_DISTANCE;
        double baseY = eyeY + camForwardY * ARROW_BASE_DISTANCE;
        double baseZ = playerZ + camForwardZ * ARROW_BASE_DISTANCE;

        double relativeYaw = Math.toRadians(targetYaw - cameraYaw);
        double targetPitchRad = Math.toRadians(targetPitch);

        double targetDirX = dx / horizontalDist;
        double targetDirZ = dz / horizontalDist;

        double offsetX = targetDirX * ARROW_SPREAD_RADIUS;
        double offsetZ = targetDirZ * ARROW_SPREAD_RADIUS;
        double offsetY = Math.sin(targetPitchRad) * ARROW_SPREAD_RADIUS;

        double forwardOffset = Math.cos(relativeYaw);
        if (forwardOffset < 0) {
            double behindFactor = 1.0 + (-forwardOffset * 0.5);
            offsetX *= behindFactor;
            offsetZ *= behindFactor;
        }

        double arrowX = baseX + offsetX;
        double arrowY = baseY + offsetY;
        double arrowZ = baseZ + offsetZ;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float alpha = ARROW_ALPHA;

        double renderX = arrowX - playerX;
        double renderY = arrowY - playerY;
        double renderZ = arrowZ - playerZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY, renderZ);

        GlStateManager.rotate(180 + targetYaw, 0, 1, 0);
        GlStateManager.rotate(targetPitch, 1, 0, 0);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GL11.glDepthRange(0.0, 0.001);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        // Apply the arrow scale
        float arrowScale = 1.0f;
        float halfThick = (ARROW_THICKNESS / 2) * arrowScale;
        float len = ARROW_LENGTH * arrowScale;
        float w = ARROW_WIDTH * arrowScale;
        float offset = BASE_TEXT_HEIGHT_OFFSET * arrowScale;

        // Render gradient arrow using rings from base to tip
        for (int i = 0; i < GRADIENT_RINGS; i++) {
            float t1 = (float) i / GRADIENT_RINGS;
            float t2 = (float) (i + 1) / GRADIENT_RINGS;

            float curved1 = (float) Math.pow(t1, GRADIENT_CURVE);
            float curved2 = (float) Math.pow(t2, GRADIENT_CURVE);

            float z1 = -len * t1;
            float z2 = -len * t2;
            float w1 = w * (1.0f - t1);
            float w2 = w * (1.0f - t2);

            float factor1 = GRADIENT_START_FACTOR + (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR) * curved1;
            float factor2 = GRADIENT_START_FACTOR + (GRADIENT_END_FACTOR - GRADIENT_START_FACTOR) * curved2;

            float r1 = r * factor1;
            float g1 = g * factor1;
            float b1 = b * factor1;
            float r2 = r * factor2;
            float g2 = g * factor2;
            float b2 = b * factor2;

            // Top face
            buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
            buffer.pos(w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
            buffer.pos(w2, halfThick, z2).color(r2, g2, b2, alpha).endVertex();

            buffer.pos(-w1, halfThick, z1).color(r1, g1, b1, alpha).endVertex();
            buffer.pos(w2, halfThick, z2).color(r2, g2, b2, alpha).endVertex();
            buffer.pos(-w2, halfThick, z2).color(r2, g2, b2, alpha).endVertex();

            // Bottom face
            float bottomFactor1 = factor1 * 0.7f;
            float bottomFactor2 = factor2 * 0.7f;
            float rb1 = r * bottomFactor1;
            float gb1 = g * bottomFactor1;
            float bb1 = b * bottomFactor1;
            float rb2 = r * bottomFactor2;
            float gb2 = g * bottomFactor2;
            float bb2 = b * bottomFactor2;

            buffer.pos(w1, -halfThick, z1).color(rb1, gb1, bb1, alpha).endVertex();
            buffer.pos(-w1, -halfThick, z1).color(rb1, gb1, bb1, alpha).endVertex();
            buffer.pos(-w2, -halfThick, z2).color(rb2, gb2, bb2, alpha).endVertex();

            buffer.pos(w1, -halfThick, z1).color(rb1, gb1, bb1, alpha).endVertex();
            buffer.pos(-w2, -halfThick, z2).color(rb2, gb2, bb2, alpha).endVertex();
            buffer.pos(w2, -halfThick, z2).color(rb2, gb2, bb2, alpha).endVertex();

            // Left side
            buffer.pos(-w1, -halfThick, z1).color(r1 * 0.8f, g1 * 0.8f, b1 * 0.8f, alpha).endVertex();
            buffer.pos(-w1, halfThick, z1).color(r1 * 0.8f, g1 * 0.8f, b1 * 0.8f, alpha).endVertex();
            buffer.pos(-w2, halfThick, z2).color(r2 * 0.8f, g2 * 0.8f, b2 * 0.8f, alpha).endVertex();

            buffer.pos(-w1, -halfThick, z1).color(r1 * 0.8f, g1 * 0.8f, b1 * 0.8f, alpha).endVertex();
            buffer.pos(-w2, halfThick, z2).color(r2 * 0.8f, g2 * 0.8f, b2 * 0.8f, alpha).endVertex();
            buffer.pos(-w2, -halfThick, z2).color(r2 * 0.8f, g2 * 0.8f, b2 * 0.8f, alpha).endVertex();

            // Right side
            buffer.pos(w1, halfThick, z1).color(r1 * 0.8f, g1 * 0.8f, b1 * 0.8f, alpha).endVertex();
            buffer.pos(w1, -halfThick, z1).color(r1 * 0.8f, g1 * 0.8f, b1 * 0.8f, alpha).endVertex();
            buffer.pos(w2, -halfThick, z2).color(r2 * 0.8f, g2 * 0.8f, b2 * 0.8f, alpha).endVertex();

            buffer.pos(w1, halfThick, z1).color(r1 * 0.8f, g1 * 0.8f, b1 * 0.8f, alpha).endVertex();
            buffer.pos(w2, -halfThick, z2).color(r2 * 0.8f, g2 * 0.8f, b2 * 0.8f, alpha).endVertex();
            buffer.pos(w2, halfThick, z2).color(r2 * 0.8f, g2 * 0.8f, b2 * 0.8f, alpha).endVertex();
        }

        // Back face (base of arrow)
        float backFactor = GRADIENT_START_FACTOR * 1.2f;
        float rb = Math.min(1.0f, r * backFactor);
        float gb = Math.min(1.0f, g * backFactor);
        float bb = Math.min(1.0f, b * backFactor);

        buffer.pos(-w, -halfThick, 0).color(rb, gb, bb, alpha).endVertex();
        buffer.pos(w, -halfThick, 0).color(rb, gb, bb, alpha).endVertex();
        buffer.pos(w, halfThick, 0).color(rb, gb, bb, alpha).endVertex();

        buffer.pos(-w, -halfThick, 0).color(rb, gb, bb, alpha).endVertex();
        buffer.pos(w, halfThick, 0).color(rb, gb, bb, alpha).endVertex();
        buffer.pos(-w, halfThick, 0).color(rb, gb, bb, alpha).endVertex();

        tessellator.draw();

        GL11.glDepthRange(0.0, 1.0);
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();

        GlStateManager.popMatrix();

        // Draw distance text above arrow
        String distanceStr = formatDistance(distance);

        // Calculate text scale based on arrow's distance from camera
        double arrowDistFromCamera = Math.sqrt(renderX * renderX + renderY * renderY + renderZ * renderZ);
        float textScale = calculateTextScale(arrowDistFromCamera);

        // Calculate text offset
        float textOffset = offset + 4.0f * textScale;

        GlStateManager.pushMatrix();
        GlStateManager.translate(renderX, renderY + textOffset, renderZ);

        GlStateManager.rotate(-cameraYaw, 0, 1, 0);
        GlStateManager.rotate(cameraPitch, 1, 0, 0);
        GlStateManager.scale(-textScale, -textScale, textScale);

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();

        int textWidth = mc.fontRenderer.getStringWidth(distanceStr);
        mc.fontRenderer.drawStringWithShadow(distanceStr, -textWidth / 2.0f, 0, color | 0xFF000000);

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();

        GlStateManager.popMatrix();
    }

    /**
     * Format distance for display.
     */
    private String formatDistance(double distance) {
        if (distance < 1000) {
            return String.format("%.0fm", distance);
        } else {
            return String.format("%.1fkm", distance / 1000.0);
        }
    }

    /**
     * Calculate adaptive text scale based on arrow's distance from camera.
     */
    private float calculateTextScale(double arrowDistFromCamera) {
        float baseScale = BASE_TEXT_SCALE;

        double minDist = ARROW_BASE_DISTANCE - ARROW_SPREAD_RADIUS;
        double maxDist = ARROW_BASE_DISTANCE + ARROW_SPREAD_RADIUS * 1.5;

        float minMult = 1.0f;
        float maxMult = 2.0f;

        if (arrowDistFromCamera <= minDist) return baseScale * minMult;
        if (arrowDistFromCamera >= maxDist) return baseScale * maxMult;

        // Linear interpolation: further arrows get larger text
        float t = (float) ((arrowDistFromCamera - minDist) / (maxDist - minDist));
        float multiplier = minMult + t * (maxMult - minMult);

        return baseScale * multiplier;
    }
}
