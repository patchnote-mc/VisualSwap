package com.patchnote.visualswap.client.particles;

import com.patchnote.visualswap.VisualSwap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * The mod's custom particle types and their client sprite factories.
 *
 * <p>Sprites come from the build-time {@code bakeParticleSprites} task: each type's sprite is loaded
 * from {@code assets/visual-swap/particles/<name>.json} -> {@code textures/particle/<name>.png}.
 */
public final class ParticlesHandler
{
    public static final SimpleParticleType SWAP_POSSIBLE = FabricParticleTypes.simple();
    public static final SimpleParticleType SWAP_ATTACKED = FabricParticleTypes.simple();
    public static final SimpleParticleType SWAP_CONSECUTIVE = FabricParticleTypes.simple();

    /// Particles emitted per swap-hit; the burst scales linearly with the chain depth up to {@link #MAX_CHAIN_HITS}.
    private static final int PARTICLES_PER_HIT = 9;
    private static final int MAX_CHAIN_HITS = 4;
    /// A chain this deep (>= 2 = stun slam) emits the consecutive sprite instead of the single-hit one.
    private static final int CONSECUTIVE_MIN_HITS = 2;

    /// {@code addParticle} carries only position + velocity, so the in-flight spawn's props (for lifetime) are handed
    /// to the provider through this field. Safe because particle creation runs synchronously on the client thread
    /// inside {@code addParticle}.
    private static AttackParticleProps spawningProps = AttackParticleProps.NORMAL;

    static AttackParticleProps spawningProps() { return spawningProps; }

    private ParticlesHandler() { }

    public static void register()
    {
        registerTypes();
        registerFactories();
    }

    private static void registerTypes()
    {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, getId("swap_possible"), SWAP_POSSIBLE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, getId("swap_attacked"), SWAP_ATTACKED);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, getId("consecutive"), SWAP_CONSECUTIVE);
    }

    private static void registerFactories()
    {
        // Masks parsed once; the tint is read live per spawn so the IndicatorType config takes effect immediately.
        SwapHitMasks.Mask possible = SwapHitMasks.possible();
        SwapHitMasks.Mask attacked = SwapHitMasks.attacked();
        SwapHitMasks.Mask consecutive = SwapHitMasks.consecutive();

        ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
        registry.register(SWAP_POSSIBLE, sprites -> SwapParticleProvider.possible(sprites, () -> possible.particleColor() & 0xFFFFFF));
        registry.register(SWAP_ATTACKED, sprites -> SwapParticleProvider.attacked(sprites, () -> attacked.particleColor() & 0xFFFFFF));
        registry.register(SWAP_CONSECUTIVE, sprites -> SwapParticleProvider.consecutive(sprites, () -> consecutive.particleColor() & 0xFFFFFF));
    }

    private static Identifier getId(String path) { return Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, path); }


    /// {@code chainHits} is this hit's position in the consecutive-swap chain (1 = single). A chain of
    /// {@link #CONSECUTIVE_MIN_HITS} or more emits the consecutive sprite; the burst grows with the chain, clamped at
    /// {@link #MAX_CHAIN_HITS} so a long stun-slam can't flood the screen. {@code props} makes the particles spew (and
    /// linger) like the vanilla attack that landed (crit pops up, mace smash bursts outward).
    public static void spawnParticles(Minecraft client, Entity target, int chainHits, AttackParticleProps props)
    {
        if (client.level == null) return;

        spawningProps = props;
        ParticleOptions particle = chainHits >= CONSECUTIVE_MIN_HITS ? SWAP_CONSECUTIVE : SWAP_ATTACKED;
        int count = PARTICLES_PER_HIT * Math.clamp(chainHits, 1, MAX_CHAIN_HITS);
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

            client.level.addParticle(particle, cx + ox, cy + oy, cz + oz, vx, vy, vz);
        }
    }
}
