package com.yg.ae2itemlocator.client;

import com.yg.ae2itemlocator.common.CellRef;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Draws the drive -> controller link as vanilla dust particles in the highlight colour.
 *
 * <p>Particles were chosen over own geometry on purpose: they are always visible through blocks
 * (they are not depth-tested against the world during their pass), they need no render type of our
 * own, and they read as a "connection" rather than as part of the drive.
 */
public final class LinkParticles {

    /** How many particles per block of distance, and the hard cap per tick. */
    private static final double PER_BLOCK = 1.6d;
    private static final int MIN_COUNT = 4;
    private static final int MAX_COUNT = 28;

    public static void tick() {
        if (!HighlightState.isLocked() || !HighlightState.hasRefs()) {
            return;
        }

        var minecraft = net.minecraft.client.Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) {
            return;
        }

        List<CellRef> refs = HighlightState.refs();
        int color = ClientConfig.driveColor();
        Vector3f rgb = new Vector3f(
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f);
        var particle = new DustParticleOptions(rgb, 1.15f);
        var random = ThreadLocalRandom.current();

        for (CellRef ref : refs) {
            if (ref.link() == null || !ref.dimension().equals(level.dimension())) {
                continue;
            }

            double x1 = ref.pos().getX() + 0.5;
            double y1 = ref.pos().getY() + 0.5;
            double z1 = ref.pos().getZ() + 0.5;
            double x2 = ref.link().getX() + 0.5;
            double y2 = ref.link().getY() + 0.5;
            double z2 = ref.link().getZ() + 0.5;

            double distance = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
                    + (z2 - z1) * (z2 - z1));
            int count = (int) Math.min(MAX_COUNT, Math.max(MIN_COUNT, distance * PER_BLOCK));

            for (int i = 0; i < count; i++) {
                double t = (i + random.nextDouble()) / count;
                double x = x1 + (x2 - x1) * t + (random.nextDouble() - 0.5) * 0.18;
                double y = y1 + (y2 - y1) * t + (random.nextDouble() - 0.5) * 0.18;
                double z = z1 + (z2 - z1) * t + (random.nextDouble() - 0.5) * 0.18;
                level.addParticle(particle, x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }

    private LinkParticles() {
    }
}
