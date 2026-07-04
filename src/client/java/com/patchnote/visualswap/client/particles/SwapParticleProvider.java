package com.patchnote.visualswap.client.particles;

import com.patchnote.visualswap.client.config.ModConfig;
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
    private final float gravity;
    private final float friction;
    private final float speed;
    private final float upBias;

    private SwapParticleProvider(SpriteSet sprites, IntSupplier rgb, float baseSize, float gravity, float friction,
                                 float speed, float upBias)
    {
        this.sprites = sprites;
        this.rgb = rgb;
        this.baseSize = baseSize;
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
        final ModConfig config = ModConfig.get();

        // xAux/yAux/zAux carry the attack impulse (see AttackParticleProps); the provider's own jitter rides on top.
        double dx = xAux + (random.nextDouble() - 0.5) * 2.0 * this.speed;
        double dy = yAux + (random.nextDouble() - 0.5) * 2.0 * this.speed + this.upBias;
        double dz = zAux + (random.nextDouble() - 0.5) * 2.0 * this.speed;

        AttackParticleProps props = ParticlesHandler.spawningProps();
        int lifetime = props.rollLifetime(random);

        // size
        float multipliers = 1.0f;
        multipliers *= (0.85f + random.nextFloat() * 0.3f);
        multipliers *= config.sizeMultiplier();
        float size = this.baseSize * multipliers;

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

    public static SwapParticleProvider possible(SpriteSet sprites)
    {
        return new SwapParticleProvider(
                sprites, () -> SwapHitMasks.possible().particleColor() & 0xFFFFFF, //
                0.15f, 0.012f, 0.92f, 0.05f, 0.045f
        );
    }

    public static SwapParticleProvider attacked(SpriteSet sprites)
    {
        return new SwapParticleProvider(
                sprites, () -> SwapHitMasks.attacked().particleColor() & 0xFFFFFF, //
                0.12f, 0.18f, 0.70f, 0.22f, 0.10f
        );
    }

    public static SwapParticleProvider consecutive(SpriteSet sprites)
    {
        return new SwapParticleProvider(
                sprites, () -> SwapHitMasks.consecutive().particleColor() & 0xFFFFFF, //
                0.15f, 0.16f, 0.72f, 0.28f, 0.14f
        );
    }
}