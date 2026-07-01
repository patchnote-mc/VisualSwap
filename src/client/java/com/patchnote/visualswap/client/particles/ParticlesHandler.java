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
    /// {@link #MAX_CHAIN_HITS} so a long stun-slam can't flood the screen.
    public static void spawnParticles(Minecraft client, Entity target, int chainHits)
    {
        if (client.level == null) return;

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
            client.level.addParticle(particle, cx + ox, cy + oy, cz + oz, 0.0, 0.0, 0.0);
        }
    }
}
