package com.patchnote.visualswap.client.particles;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/// Registered particle type for one swap-hit sprite tier. Carries {@link SwapParticleOptions} so each spawn's full
/// property set travels with the particle.
public final class SwapParticleType extends ParticleType<SwapParticleOptions>
{
    public SwapParticleType() { super(false); }

    @Override
    public MapCodec<SwapParticleOptions> codec() { return SwapParticleOptions.codec(this); }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, SwapParticleOptions> streamCodec()
    {
        return SwapParticleOptions.streamCodec(this);
    }
}
