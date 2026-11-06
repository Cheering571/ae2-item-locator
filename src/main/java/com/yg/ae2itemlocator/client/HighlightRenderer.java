package com.yg.ae2itemlocator.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
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
import net.minecraftforge.client.event.RenderLevelStageEvent;

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
    /**
     * The controller's shell is drawn through walls, so all six faces stack inside the buffer
     * instead of the three the camera can see on a drive. It therefore needs roughly a third of the
     * drive's alpha to read the same, otherwise the block becomes a solid coloured blob at night.
     */
    private static final float HOST_SHELL_ALPHA = 0.06f;

    /**
     * The link is drawn as three nested tubes instead of one solid one: a bright thin core and two
     * progressively wider, fainter halos. A single opaque tube reads as a hard plastic rod; the
     * falloff makes it look like a soft glowing thread.
     */
    private static final float[][] LINK_LAYERS = {
            { 0.018f, 0.85f },
            { 0.048f, 0.30f },
            { 0.090f, 0.10f },
    };

    /** Edge half-width of the controller outline, matched to the drive's line boxes. */
    private static final float[] HOST_EDGE_HALF = { 0.004f, 0.006f };
    private static final float[] HOST_EDGE_ALPHA = { 1.0f, 0.55f };
    private static final float[] HOST_EDGE_INFLATE = { 0.002f, 0.010f };

    /**
     * Quads that ignore the depth buffer, so the link beam and the controller box stay visible
     * through the multiblock casing. {@code NO_DEPTH_TEST} plus {@code COLOR_WRITE} keeps them from
     * touching the depth buffer at all, which also stops them from hiding anything behind them.
     */
    // 1.20.1 keeps the composite-state shards protected, so a custom pipeline is out of reach
    // without an access transformer. debugQuads is the same POSITION_COLOR quad pipeline and is
    // already depth-test free, which is exactly what a through-wall overlay needs.
    private static final RenderType THROUGH_WALLS = RenderType.debugQuads();

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

            // Multiblock controller: same treatment as a drive — translucent shell plus the same two
            // nested edge boxes — drawn through walls because it sits inside the casing. The link
            // itself is a soft three-layer beam along the straight line.
            VertexConsumer ghost = buffers.getBuffer(THROUGH_WALLS);
            boolean drewGhost = false;
            for (CellRef ref : HighlightState.refs()) {
                if (ref.link() == null || !ref.dimension().equals(level.dimension())) {
                    continue;
                }

                shell(pose, ghost, ref.link(), dr, dg, db, HOST_SHELL_ALPHA);
                for (int i = 0; i < HOST_EDGE_HALF.length; i++) {
                    wireBox(pose, ghost, ref.link(), HOST_EDGE_INFLATE[i],
                            HOST_EDGE_HALF[i], HOST_EDGE_ALPHA[i], dr, dg, db);
                }

                float x1 = ref.pos().getX() + 0.5f;
                float y1 = ref.pos().getY() + 0.5f;
                float z1 = ref.pos().getZ() + 0.5f;
                float x2 = ref.link().getX() + 0.5f;
                float y2 = ref.link().getY() + 0.5f;
                float z2 = ref.link().getZ() + 0.5f;
                for (float[] layer : LINK_LAYERS) {
                    tube(pose, ghost, x1, y1, z1, x2, y2, z2, layer[0], layer[1], dr, dg, db);
                }

                drewGhost = true;
            }
            if (drewGhost) {
                buffers.endBatch(THROUGH_WALLS);
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
        shell(pose, consumer, pos, r, g, b, SHELL_ALPHA);
    }

    private static void shell(PoseStack pose, VertexConsumer consumer, BlockPos pos,
            float r, float g, float b, float alpha) {
        float x1 = pos.getX() - 0.005f;
        float y1 = pos.getY() - 0.005f;
        float z1 = pos.getZ() - 0.005f;
        float x2 = pos.getX() + 1.005f;
        float y2 = pos.getY() + 1.005f;
        float z2 = pos.getZ() + 1.005f;

        quad(pose, consumer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, alpha, r, g, b); // -Y
        quad(pose, consumer, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, alpha, r, g, b); // +Y
        quad(pose, consumer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, alpha, r, g, b); // -Z
        quad(pose, consumer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, alpha, r, g, b); // +Z
        quad(pose, consumer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, alpha, r, g, b); // -X
        quad(pose, consumer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, alpha, r, g, b); // +X
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
     * The twelve edges of a block as thin tubes, so the outline is visible from any angle without
     * relying on GL line width. Used for the controller, which is drawn through walls.
     */
    private static void wireBox(PoseStack pose, VertexConsumer consumer, BlockPos pos,
            float inflate, float half, float alpha, float r, float g, float b) {
        float x1 = pos.getX() - inflate;
        float y1 = pos.getY() - inflate;
        float z1 = pos.getZ() - inflate;
        float x2 = x1 + 1f + inflate * 2f;
        float y2 = y1 + 1f + inflate * 2f;
        float z2 = z1 + 1f + inflate * 2f;

        // bottom rectangle
        tube(pose, consumer, x1, y1, z1, x2, y1, z1, half, alpha, r, g, b);
        tube(pose, consumer, x2, y1, z1, x2, y1, z2, half, alpha, r, g, b);
        tube(pose, consumer, x2, y1, z2, x1, y1, z2, half, alpha, r, g, b);
        tube(pose, consumer, x1, y1, z2, x1, y1, z1, half, alpha, r, g, b);
        // top rectangle
        tube(pose, consumer, x1, y2, z1, x2, y2, z1, half, alpha, r, g, b);
        tube(pose, consumer, x2, y2, z1, x2, y2, z2, half, alpha, r, g, b);
        tube(pose, consumer, x2, y2, z2, x1, y2, z2, half, alpha, r, g, b);
        tube(pose, consumer, x1, y2, z2, x1, y2, z1, half, alpha, r, g, b);
        // verticals
        tube(pose, consumer, x1, y1, z1, x1, y2, z1, half, alpha, r, g, b);
        tube(pose, consumer, x2, y1, z1, x2, y2, z1, half, alpha, r, g, b);
        tube(pose, consumer, x2, y1, z2, x2, y2, z2, half, alpha, r, g, b);
        tube(pose, consumer, x1, y1, z2, x1, y2, z2, half, alpha, r, g, b);
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
        org.joml.Matrix4f matrix = pose.last().pose();
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        int cr = Math.max(0, Math.min(255, (int) (r * 255f)));
        int cg = Math.max(0, Math.min(255, (int) (g * 255f)));
        int cb = Math.max(0, Math.min(255, (int) (b * 255f)));
        consumer.vertex(matrix, x1, y1, z1).color(cr, cg, cb, a).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(cr, cg, cb, a).endVertex();
        consumer.vertex(matrix, x3, y3, z3).color(cr, cg, cb, a).endVertex();
        consumer.vertex(matrix, x4, y4, z4).color(cr, cg, cb, a).endVertex();
    }

    private HighlightRenderer() {
    }
}
