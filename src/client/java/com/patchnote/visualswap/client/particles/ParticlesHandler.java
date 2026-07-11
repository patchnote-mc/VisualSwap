package com.patchnote.visualswap.client.particles;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * Spawns the in-world swap-hit particle burst.
 *
 * <p>Deliberately does <b>not</b> register a custom {@link net.minecraft.core.particles.ParticleType}. The particle
 * registry is network-synced, and a client-only mod that adds entries to it pollutes the integrated server's registry
 * when the player opens their world to LAN — Fabric's registry-sync then disconnects any joining client that lacks the
 * mod. Instead we build {@link SwapParticle}s directly and hand them to the vanilla {@link ParticleEngine}, keeping the
 * effect purely client-local with zero server-visible footprint.
 *
 * <p>Sprites are the build-time {@code bakeParticleSprites} output ({@code textures/particle/<name>.png}), stitched
 * into the vanilla particle atlas by its directory source, so they are looked up by id at spawn time — no particle
 * definition JSON or {@code SpriteSet} needed.
 */
public final class ParticlesHandler
{
    /// Per-tier spawn physics. Colour + lifetime are supplied per spawn.
    private enum Tier
    {
        ATTACKED("swap_attacked", 0.12f, 0.18f, 0.70f, 0.22f, 0.10f),
        CONSECUTIVE("swap_consecutive", 0.15f, 0.16f, 0.72f, 0.28f, 0.14f);

        private final Identifier sprite;
        private final float baseSize;
        private final float gravity;
        private final float friction;
        /// Half-width of the per-particle velocity jitter added on top of the carried impulse.
        private final float speed;
        private final float upBias;

        Tier(String spriteName, float baseSize, float gravity, float friction, float speed, float upBias)
        {
            this.sprite = Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, spriteName);
            this.baseSize = baseSize;
            this.gravity = gravity;
            this.friction = friction;
            this.speed = speed;
            this.upBias = upBias;
        }
    }

    private static final int PARTICLES_PER_HIT = 9;
    private static final int MAX_CHAIN_HITS = 4;
    /// A chain this deep (>= 2 = stun slam) emits the consecutive sprite instead of the single-hit one.
    private static final int CONSECUTIVE_MIN_HITS = 2;

    private ParticlesHandler() { }

    /* FUNCTIONS */

    public static void spawnParticles(Minecraft client, Entity target, int chainHits, AttackParticleProps props)
    {
        ClientLevel level = client.level;
        if (level == null) return;
        if (!ModConfig.get().particlesActive()) return;

        boolean consecutive = chainHits >= CONSECUTIVE_MIN_HITS;
        Tier tier = consecutive ? Tier.CONSECUTIVE : Tier.ATTACKED;
        TextureAtlasSprite sprite = spriteFor(client, tier);

        int rgb = (consecutive ? SwapHitMasks.consecutive() : SwapHitMasks.attacked()).particleColor() & 0xFFFFFF;
        int count = PARTICLES_PER_HIT * Math.clamp(chainHits, 1, MAX_CHAIN_HITS);
        double sizeScale = ModConfig.get().getSize();
        double spread = 0.45;
        RandomSource random = target.getRandom();
        double cx = target.getX();
        double cy = target.getY(0.5);
        double cz = target.getZ();
        for (int i = 0; i < count; i++)
        {
            double ox = random.nextGaussian() * spread;
            double oy = random.nextGaussian() * spread;
            double oz = random.nextGaussian() * spread;

            // Push each particle radially out from the target axis; near-center ones get a random bearing.
            double hlen = Math.sqrt(ox * ox + oz * oz);
            double nx, nz;
            if (hlen < 1.0e-4)
            {
                double angle = random.nextDouble() * Math.PI * 2.0;
                nx = Math.cos(angle);
                nz = Math.sin(angle);
            }
            else
            {
                nx = ox / hlen;
                nz = oz / hlen;
            }
            double vx = nx * props.outward();
            double vy = props.up();
            double vz = nz * props.outward();

            spawnOne(level, client.particleEngine, tier, sprite, rgb, sizeScale,
                     cx + ox, cy + oy, cz + oz, vx, vy, vz, props.rollLifetime(random), random);
        }
    }

    /* HELPERS */

    /// Builds one particle from its tier physics plus per-particle jitter (the carried impulse is `vx/vy/vz`) and hands
    /// it to the vanilla engine — the direct equivalent of the old {@code SpriteParticleProvider} spawn path.
    private static void spawnOne(ClientLevel level, ParticleEngine engine, Tier tier, TextureAtlasSprite sprite,
                                 int rgb, double sizeScale, double x, double y, double z,
                                 double vx, double vy, double vz, int lifetime, RandomSource random)
    {
        double dx = vx + (random.nextDouble() - 0.5) * 2.0 * tier.speed;
        double dy = vy + (random.nextDouble() - 0.5) * 2.0 * tier.speed + tier.upBias;
        double dz = vz + (random.nextDouble() - 0.5) * 2.0 * tier.speed;

        float size = (float) (tier.baseSize * (0.85f + random.nextFloat() * 0.3f) * sizeScale);

        SwapParticle particle = new SwapParticle(level, x, y, z, dx, dy, dz, sprite, rgb, size, lifetime,
                                                 tier.gravity, tier.friction);
        particle.tick();
        engine.add(particle);
    }

    /// The tier's sprite, looked up from the vanilla particle atlas (returns the missing-texture sprite if absent).
    private static TextureAtlasSprite spriteFor(Minecraft client, Tier tier)
    {
        return client.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(tier.sprite);
    }
}
