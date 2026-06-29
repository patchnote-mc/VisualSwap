package com.patchnote.visualswap.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.NonNull;

/**
 * The in-world swap-hit glyph particle — a single camera-facing quad whose sprite is the baked swap-hit mask (see
 * {@link com.patchnote.visualswap.SwapHitMasks} / the baked PNG). It mirrors the vanilla {@code CritParticle} but in
 * two flavors, one per swap-hit tier, distinguished by their {@link Provider}:
 * <ul>
 *   <li><b>possible</b> ({@link Provider#possible}) — a few glyphs that float up gently and fade;</li>
 *   <li><b>attacked</b> ({@link Provider#attacked}) — a crit-like outward spray with gravity.</li>
 * </ul>
 *
 * <p>The sprite PNG is opaque-on-transparent (the mask shape), so we render on the
 * {@link SingleQuadParticle.Layer#TRANSLUCENT} layer and fade {@link #alpha} over the last
 * portion of the lifetime. RGB tint comes from the mask's {@code particleColor}.
 */
public class SwapGlyphParticle extends SingleQuadParticle
{

    /** Fraction of lifetime over which alpha eases from 1 to 0 at the end. */
    private static final float FADE_FRACTION = 0.5f;

    private SwapGlyphParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za,
                              TextureAtlasSprite sprite, int rgb, float baseSize, int lifetime, float gravity,
                              float friction)
    {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprite);
        this.xd = xa;
        this.yd = ya;
        this.zd = za;
        this.gravity = gravity;
        this.friction = friction;
        this.hasPhysics = false;
        this.lifetime = lifetime;
        this.quadSize = baseSize;
        this.rCol = ((rgb >> 16) & 0xFF) / 255.0f;
        this.gCol = ((rgb >> 8) & 0xFF) / 255.0f;
        this.bCol = (rgb & 0xFF) / 255.0f;
        this.alpha = 1.0f;
    }

    @Override
    public void tick()
    {
        super.tick();
        float t = (float) this.age / (float) this.lifetime;
        float fadeStart = 1.0f - FADE_FRACTION;
        this.alpha = t <= fadeStart ? 1.0f : Mth.clamp(1.0f - (t - fadeStart) / FADE_FRACTION, 0.0f, 1.0f);
    }

    @Override
    public SingleQuadParticle.@NonNull Layer getLayer() { return SingleQuadParticle.Layer.TRANSLUCENT; }

    /**
     * Per-tier provider. Built via {@link #possible}/{@link #attacked} so the two registered particle types
     * ({@code VisualSwapParticles.SWAP_POSSIBLE} / {@code SWAP_ATTACKED}) get the right motion + tint.
     */
    public static final class Provider implements ParticleProvider<SimpleParticleType>
    {
        private final SpriteSet sprites;
        private final int rgb;
        private final float baseSize;
        private final int minLifetime;
        private final int maxLifetime;
        private final float gravity;
        private final float friction;
        private final float speed;
        private final float upBias;

        private Provider(SpriteSet sprites, int rgb, float baseSize, int minLifetime, int maxLifetime, float gravity,
                         float friction, float speed, float upBias)
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

        /** Normal swap hit — slow, gentle upward drift, spread out, long fade. */
        public static Provider possible(SpriteSet sprites, int rgb)
        {
            return new Provider(sprites, rgb, 0.15f, 18, 26, 0.012f, 0.92f, 0.05f, 0.045f);
        }

        /** Consecutive swap hit — fast crit-like outward spray with gravity, wider spread. */
        public static Provider attacked(SpriteSet sprites, int rgb)
        {
            return new Provider(sprites, rgb, 0.12f, 10, 16, 0.18f, 0.70f, 0.22f, 0.10f);
        }

        @Override
        public Particle createParticle(@NonNull SimpleParticleType options, @NonNull ClientLevel level, double x,
                                       double y, double z, double xAux, double yAux, double zAux, RandomSource random)
        {
            double dx = (random.nextDouble() - 0.5) * 2.0 * this.speed;
            double dy = (random.nextDouble() - 0.5) * 2.0 * this.speed + this.upBias;
            double dz = (random.nextDouble() - 0.5) * 2.0 * this.speed;
            int lifetime = this.minLifetime + random.nextInt(this.maxLifetime - this.minLifetime + 1);
            float size = this.baseSize * (0.85f + random.nextFloat() * 0.3f);
            SwapGlyphParticle particle = new SwapGlyphParticle(
                    level,
                    x,
                    y,
                    z,
                    dx,
                    dy,
                    dz,
                    this.sprites.get(random),
                    this.rgb,
                    size,
                    lifetime,
                    this.gravity,
                    this.friction
            );
            particle.tick();
            return particle;
        }
    }
}
