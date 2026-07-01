package com.patchnote.visualswap.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

import java.util.function.IntSupplier;

/// Provider for each particle in {@code swap_hit_masks.json}
public final class SwapParticleProvider implements ParticleProvider<SimpleParticleType>
{
    private final SpriteSet sprites;
    private final IntSupplier rgb;
    private final float baseSize;
    private final int minLifetime;
    private final int maxLifetime;
    private final float gravity;
    private final float friction;
    private final float speed;
    private final float upBias;

    private SwapParticleProvider(SpriteSet sprites, IntSupplier rgb, float baseSize, int minLifetime, int maxLifetime,
                                 float gravity, float friction, float speed, float upBias)
    {
        this.sprites = sprites;
        this.rgb = rgb;
        this.baseSize = baseSize;
        this.minLifetime = minLifetime;
        this.maxLifetime = maxLifetime;
        this.gravity = gravity;
        this.friction = friction;
        this.speed = speed;
        this.upBias = upBias;
    }

    /* OVERRIDES */

    @Override
    public Particle createParticle(@NonNull SimpleParticleType options, @NonNull ClientLevel level, double x, double y,
                                   double z, double xAux, double yAux, double zAux, RandomSource random)
    {
        double dx = (random.nextDouble() - 0.5) * 2.0 * this.speed;
        double dy = (random.nextDouble() - 0.5) * 2.0 * this.speed + this.upBias;
        double dz = (random.nextDouble() - 0.5) * 2.0 * this.speed;
        int lifetime = this.minLifetime + random.nextInt(this.maxLifetime - this.minLifetime + 1);
        float size = this.baseSize * (0.85f + random.nextFloat() * 0.3f);
        SwapParticle particle = new SwapParticle(
                level,
                x,
                y,
                z,
                dx,
                dy,
                dz,
                this.sprites.get(random),
                this.rgb.getAsInt(),
                size,
                lifetime,
                this.gravity,
                this.friction
        );
        particle.tick();
        return particle;
    }

    /* PARTICLES */

    public static SwapParticleProvider possible(SpriteSet sprites, IntSupplier rgb)
    {
        return new SwapParticleProvider(sprites, rgb, 0.15f, 18, 26, 0.012f, 0.92f, 0.05f, 0.045f);
    }

    public static SwapParticleProvider attacked(SpriteSet sprites, IntSupplier rgb)
    {
        return new SwapParticleProvider(sprites, rgb, 0.12f, 10, 16, 0.18f, 0.70f, 0.22f, 0.10f);
    }

    /// Stun-slam burst: bigger, faster and slightly longer-lived than {@link #attacked} to sell the chained hit.
    public static SwapParticleProvider consecutive(SpriteSet sprites, IntSupplier rgb)
    {
        return new SwapParticleProvider(sprites, rgb, 0.15f, 12, 18, 0.16f, 0.72f, 0.28f, 0.14f);
    }
}