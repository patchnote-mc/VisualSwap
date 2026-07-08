package com.patchnote.visualswap.client.particles;

import com.patchnote.visualswap.client.config.ModConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

/// Builds a swap-hit particle from its {@link SwapParticleOptions} (tier physics, colour, lifetime) plus this tier's
/// sprite set and per-particle jitter — no shared spawn-time state.
public final class SwapParticleProvider implements ParticleProvider<SwapParticleOptions>
{
    private final SpriteSet sprites;

    public SwapParticleProvider(SpriteSet sprites) { this.sprites = sprites; }

    /* OVERRIDES */

    @Override
    public Particle createParticle(@NonNull SwapParticleOptions options, @NonNull ClientLevel level, double x, double y,
                                   double z, double xAux, double yAux, double zAux, RandomSource random)
    {
        final ModConfig config = ModConfig.get();

        // xAux/yAux/zAux carry the attack impulse (see ParticlesHandler#spawnParticles); the jitter rides on top.
        double dx = xAux + (random.nextDouble() - 0.5) * 2.0 * options.speed();
        double dy = yAux + (random.nextDouble() - 0.5) * 2.0 * options.speed() + options.upBias();
        double dz = zAux + (random.nextDouble() - 0.5) * 2.0 * options.speed();

        double multipliers = (0.85f + random.nextFloat() * 0.3f) * config.getSize();
        double size = options.baseSize() * multipliers;

        SwapParticle particle = new SwapParticle(
                level,
                x,
                y,
                z,
                dx,
                dy,
                dz,
                this.sprites.get(random),
                options.rgb(),
                (float) size,
                options.lifetime(),
                options.gravity(),
                options.friction()
        );
        particle.tick();
        return particle;
    }
}
