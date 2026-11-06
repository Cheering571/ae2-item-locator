package com.yg.ae2itemlocator.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yg.ae2itemlocator.common.CellRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the lock highlight: a translucent shell + wireframe around every drive that holds the
 * locked key, plus an optional vertical light column.
 *
 * <p>Two things are easy to get wrong and both were:
 * <ul>
 *   <li>The {@link PoseStack} handed to this event is a <b>fresh identity stack</b>; vertices must
 *       be camera-relative, so we translate by {@code -cameraPos} ourselves.</li>
 *   <li>{@code AFTER_TRANSLUCENT_BLOCKS} is dispatched from the translucent flush and NeoForge
 *       documents it as unreliable; {@code AFTER_PARTICLES} fires exactly once per frame.</li>
 * </ul>
 */
public final class HighlightRenderer {

    private static final float SHELL_ALPHA = 0.18f;
    private static final float BEAM_ALPHA = 0.30f;
    private static final float LINK_HALF = 0.07f;
    private static final float LINK_ALPHA = 0.55f;
    private static final float LINK_ARC_HEIGHT = 3.0f;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!HighlightState.isLocked() || !HighlightState.hasRefs()) {
            return;
        }
        probeLinks();

        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        int driveColor = ClientConfig.driveColor();
        float dr = ((driveColor >> 16) & 0xFF) / 255f;
        float dg = ((driveColor >> 8) & 0xFF) / 255f;
        float db = (driveColor & 0xFF) / 255f;

        int beamColor = ClientConfig.beamColor();
        float br = ((beamColor >> 16) & 0xFF) / 255f;
        float bg = ((beamColor >> 8) & 0xFF) / 255f;
        float bb = (beamColor & 0xFF) / 255f;

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        try {
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            boolean drewShell = false;
            for (CellRef ref : HighlightState.refs()) {
                if (ref.dimension().equals(level.dimension())) {
                    shell(pose, quads, ref.pos(), dr, dg, db);
                    drewShell = true;
                }
            }
            if (drewShell) {
                buffers.endBatch(RenderType.debugQuads());
            }

            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            boolean drewLines = false;
            for (CellRef ref : HighlightState.refs()) {
                if (ref.dimension().equals(level.dimension())) {
                    // Two nested boxes: line width is not controllable through the shader
                    // pipeline, so a second slightly larger box reads as a thicker outline.
                    LevelRenderer.renderLineBox(pose, lines, new AABB(ref.pos()).inflate(0.002),
                            dr, dg, db, 1.0f);
                    LevelRenderer.renderLineBox(pose, lines, new AABB(ref.pos()).inflate(0.010),
                            dr, dg, db, 0.55f);
                    drewLines = true;
                }
            }
            if (drewLines) {
                buffers.endBatch(RenderType.lines());
            }

            if (ClientConfig.BEAM.get()) {
                VertexConsumer beamQuads = buffers.getBuffer(RenderType.debugQuads());
                boolean drewBeam = false;
                for (CellRef ref : HighlightState.refs()) {
                    if (ref.dimension().equals(level.dimension())) {
                        beam(pose, beamQuads, ref.pos(), level, br, bg, bb);
                        drewBeam = true;
                    }
                }
                if (drewBeam) {
                    buffers.endBatch(RenderType.debugQuads());
                }
            }

            // Multiblock controllers: the drive and its controller usually sit inside the same
            // structure, so a straight segment would be buried in it. Route the link over the top
            // instead: up, across, down.
            VertexConsumer linkQuads = buffers.getBuffer(RenderType.debugQuads());
            boolean drewLink = false;
            for (CellRef ref : HighlightState.refs()) {
                if (ref.link() != null && ref.dimension().equals(level.dimension())) {
                    float x1 = ref.pos().getX() + 0.5f;
                    float z1 = ref.pos().getZ() + 0.5f;
                    float x2 = ref.link().getX() + 0.5f;
                    float z2 = ref.link().getZ() + 0.5f;
                    float y1 = ref.pos().getY() + 1.0f;
                    float y2 = ref.link().getY() + 1.0f;
                    float arc = Math.max(y1, y2) + LINK_ARC_HEIGHT;

                    tube(pose, linkQuads, x1, y1, z1, x1, arc, z1, LINK_HALF, LINK_ALPHA, dr, dg, db);
                    tube(pose, linkQuads, x1, arc, z1, x2, arc, z2, LINK_HALF, LINK_ALPHA, dr, dg, db);
                    tube(pose, linkQuads, x2, arc, z2, x2, y2, z2, LINK_HALF, LINK_ALPHA, dr, dg, db);
                    drewLink = true;
                }
            }
            if (drewLink) {
                buffers.endBatch(RenderType.debugQuads());
            }
        } finally {
            pose.popPose();
        }
    }

    /** One-shot diagnostic per distinct state, so a link line that never appears is explainable. */
    private static void probeLinks() {
        var summary = new StringBuilder();
        summary.append("refs=").append(HighlightState.refs().size());
        int withLink = 0;
        for (CellRef ref : HighlightState.refs()) {
            if (ref.link() != null) {
                withLink++;
            }
        }
        summary.append(" withLink=").append(withLink);
        String line = summary.toString();
        if (!line.equals(lastLinkProbe)) {
            lastLinkProbe = line;
            com.yg.ae2itemlocator.AE2ItemLocator.LOG.info("[ae2-disk-locator] render {}", line);
        }
    }

    private static String lastLinkProbe = "";

    /** Six translucent faces, slightly inflated so they do not z-fight with the drive block. */
    private static void shell(PoseStack pose, VertexConsumer consumer, BlockPos pos,
            float r, float g, float b) {
        float x1 = pos.getX() - 0.005f;
        float y1 = pos.getY() - 0.005f;
        float z1 = pos.getZ() - 0.005f;
        float x2 = pos.getX() + 1.005f;
        float y2 = pos.getY() + 1.005f;
        float z2 = pos.getZ() + 1.005f;

        quad(pose, consumer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, SHELL_ALPHA, r, g, b); // -Y
        quad(pose, consumer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, SHELL_ALPHA, r, g, b); // +Y
        quad(pose, consumer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, SHELL_ALPHA, r, g, b); // -Z
        quad(pose, consumer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, SHELL_ALPHA, r, g, b); // +Z
        quad(pose, consumer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, SHELL_ALPHA, r, g, b); // -X
        quad(pose, consumer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, SHELL_ALPHA, r, g, b); // +X
    }

    private static void beam(PoseStack pose, VertexConsumer consumer, BlockPos pos, Level level,
            float r, float g, float b) {
        float centerX = pos.getX() + 0.5f;
        float centerZ = pos.getZ() + 0.5f;
        float bottom = pos.getY() + 1.0f;
        float top = Math.min(level.getMaxBuildHeight(), pos.getY() + 160.0f);
        float half = 0.10f;

        quad(pose, consumer,
                centerX - half, bottom, centerZ - half,
                centerX + half, bottom, centerZ - half,
                centerX + half, top, centerZ - half,
                centerX - half, top, centerZ - half, BEAM_ALPHA, r, g, b);
        quad(pose, consumer,
                centerX + half, bottom, centerZ + half,
                centerX - half, bottom, centerZ + half,
                centerX - half, top, centerZ + half,
                centerX + half, top, centerZ + half, BEAM_ALPHA, r, g, b);
        quad(pose, consumer,
                centerX + half, bottom, centerZ - half,
                centerX + half, bottom, centerZ + half,
                centerX + half, top, centerZ + half,
                centerX + half, top, centerZ - half, BEAM_ALPHA, r, g, b);
        quad(pose, consumer,
                centerX - half, bottom, centerZ + half,
                centerX - half, bottom, centerZ - half,
                centerX - half, top, centerZ - half,
                centerX - half, top, centerZ + half, BEAM_ALPHA, r, g, b);
    }

    /**
     * A thin square tube from the drive to its multiblock controller. View-independent (four side
     * faces), so it reads as a beam from any angle without billboarding.
     */
    private static void tube(PoseStack pose, VertexConsumer consumer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float half, float alpha, float r, float g, float b) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;

        // Reference axis that is never parallel to the direction.
        float ux = 0f;
        float uy = 0f;
        float uz = 1f;
        if (Math.abs(dz) > 0.99f) {
            ux = 1f;
            uz = 0f;
        }

        // a = dir x ref, b = dir x a  -> two orthogonal offsets spanning the tube cross-section.
        float ax = dy * uz - dz * uy;
        float ay = dz * ux - dx * uz;
        float az = dx * uy - dy * ux;
        float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1.0e-5f) {
            return;
        }
        ax /= al;
        ay /= al;
        az /= al;

        float bx = dy * az - dz * ay;
        float by = dz * ax - dx * az;
        float bz = dx * ay - dy * ax;

        float ahx = ax * half;
        float ahy = ay * half;
        float ahz = az * half;
        float bhx = bx * half;
        float bhy = by * half;
        float bhz = bz * half;

        quad(pose, consumer,
                x1 + ahx + bhx, y1 + ahy + bhy, z1 + ahz + bhz,
                x1 + ahx - bhx, y1 + ahy - bhy, z1 + ahz - bhz,
                x2 + ahx - bhx, y2 + ahy - bhy, z2 + ahz - bhz,
                x2 + ahx + bhx, y2 + ahy + bhy, z2 + ahz + bhz, alpha, r, g, b);
        quad(pose, consumer,
                x1 - ahx - bhx, y1 - ahy - bhy, z1 - ahz - bhz,
                x1 - ahx + bhx, y1 - ahy + bhy, z1 - ahz + bhz,
                x2 - ahx + bhx, y2 - ahy + bhy, z2 - ahz + bhz,
                x2 - ahx - bhx, y2 - ahy - bhy, z2 - ahz - bhz, alpha, r, g, b);
        quad(pose, consumer,
                x1 + ahx - bhx, y1 + ahy - bhy, z1 + ahz - bhz,
                x1 - ahx - bhx, y1 - ahy - bhy, z1 - ahz - bhz,
                x2 - ahx - bhx, y2 - ahy - bhy, z2 - ahz - bhz,
                x2 + ahx - bhx, y2 + ahy - bhy, z2 + ahz - bhz, alpha, r, g, b);
        quad(pose, consumer,
                x1 - ahx + bhx, y1 - ahy + bhy, z1 - ahz + bhz,
                x1 + ahx + bhx, y1 + ahy + bhy, z1 + ahz + bhz,
                x2 + ahx + bhx, y2 + ahy + bhy, z2 + ahz + bhz,
                x2 - ahx + bhx, y2 - ahy + bhy, z2 - ahz + bhz, alpha, r, g, b);
    }

    private static void quad(PoseStack pose,
            VertexConsumer consumer,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float alpha, float r, float g, float b) {
        PoseStack.Pose entry = pose.last();
        consumer.addVertex(entry, x1, y1, z1).setColor(r, g, b, alpha);
        consumer.addVertex(entry, x2, y2, z2).setColor(r, g, b, alpha);
        consumer.addVertex(entry, x3, y3, z3).setColor(r, g, b, alpha);
        consumer.addVertex(entry, x4, y4, z4).setColor(r, g, b, alpha);
    }

    private HighlightRenderer() {
    }
}
