package com.patchnote.visualswap.client;

import com.patchnote.visualswap.SwapHitMasks;
import com.patchnote.visualswap.VisualSwap;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * The mod's custom particle types and their client sprite factories. Registered from the client entrypoint; the
 * built-in registries are still unfrozen at client-init time (Fabric freezes them later in {@code Minecraft.<init>}),
 * so no separate common/main entrypoint is needed.
 *
 * <p>Sprites come from the build-time {@code bakeParticleSprites} task: each type's sprite is loaded
 * from {@code assets/visual-swap/particles/<name>.json} -> {@code textures/particle/<name>.png}.
 */
public final class VisualSwapParticles
{
    public static final SimpleParticleType SWAP_POSSIBLE = FabricParticleTypes.simple();
    public static final SimpleParticleType SWAP_ATTACKED = FabricParticleTypes.simple();

    private VisualSwapParticles() { }

    public static void registerTypes()
    {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("swap_possible"), SWAP_POSSIBLE);
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, id("swap_attacked"), SWAP_ATTACKED);
    }

    public static void registerFactories()
    {
        int possibleRgb = SwapHitMasks.possible().particleColor() & 0xFFFFFF;
        int attackedRgb = SwapHitMasks.attacked().particleColor() & 0xFFFFFF;

        ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();
        registry.register(SWAP_POSSIBLE, sprites -> SwapGlyphParticle.Provider.possible(sprites, possibleRgb));
        registry.register(SWAP_ATTACKED, sprites -> SwapGlyphParticle.Provider.attacked(sprites, attackedRgb));
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, path); }
}
