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
    }

    private static void registerFactories()
    {
        // Masks parsed once; the tint is read live per spawn so the IndicatorType config takes effect immediately.
        SwapHitMasks.Mask possible = SwapHitMasks.possible();
        SwapHitMasks.Mask attacked = SwapHitMasks.attacked();

        ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
        registry.register(SWAP_POSSIBLE, sprites -> SwapParticleProvider.possible(sprites, () -> possible.particleColor() & 0xFFFFFF));
        registry.register(SWAP_ATTACKED, sprites -> SwapParticleProvider.attacked(sprites, () -> attacked.particleColor() & 0xFFFFFF));
    }

    private static Identifier getId(String path) { return Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, path); }


    public static void spawnParticles(Minecraft client, Entity target, ParticleOptions particle)
    {
        if (client.level == null) return;

        int count = 18;
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
