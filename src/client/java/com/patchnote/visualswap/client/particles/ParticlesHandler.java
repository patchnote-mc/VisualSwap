package com.patchnote.visualswap.client.particles;

import com.patchnote.visualswap.VisualSwap;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
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
    public static final SwapParticleType SWAP_POSSIBLE = new SwapParticleType();
    public static final SwapParticleType SWAP_ATTACKED = new SwapParticleType();
    public static final SwapParticleType SWAP_CONSECUTIVE = new SwapParticleType();

    private static final int PARTICLES_PER_HIT = 9;
    private static final int MAX_CHAIN_HITS = 4;
    /// A chain this deep (>= 2 = stun slam) emits the consecutive sprite instead of the single-hit one.
    private static final int CONSECUTIVE_MIN_HITS = 2;

    private ParticlesHandler() { }

    /* REGISTRATION */

    public static void register()
    {
        registerTypes();
        registerFactories();
    }

    private static void registerTypes()
    {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, getId("swap_possible"), SWAP_POSSIBLE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, getId("swap_attacked"), SWAP_ATTACKED);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, getId("swap_consecutive"), SWAP_CONSECUTIVE);
    }

    private static void registerFactories()
    {
        ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
        registry.register(SWAP_POSSIBLE, SwapParticleProvider::new);
        registry.register(SWAP_ATTACKED, SwapParticleProvider::new);
        registry.register(SWAP_CONSECUTIVE, SwapParticleProvider::new);
    }

    /* FUNCTIONS */

    public static void spawnParticles(Minecraft client, Entity target, int chainHits, AttackParticleProps props)
    {
        if (client.level == null) return;

        boolean consecutive = chainHits >= CONSECUTIVE_MIN_HITS;
        SwapParticleType type = consecutive ? SWAP_CONSECUTIVE : SWAP_ATTACKED;
        int rgb = (consecutive ? SwapHitMasks.consecutive() : SwapHitMasks.attacked()).particleColor() & 0xFFFFFF;
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

            SwapParticleOptions options = consecutive
                                          ? SwapParticleOptions.consecutive(type, rgb, props.rollLifetime(random))
                                          : SwapParticleOptions.attacked(type, rgb, props.rollLifetime(random));

            client.level.addParticle(options, cx + ox, cy + oy, cz + oz, vx, vy, vz);
        }
    }

    /* HELPERS */

    private static Identifier getId(String path) { return Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, path); }
}
