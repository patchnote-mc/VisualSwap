package com.patchnote.visualswap.client.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/// Vanilla-style {@link ParticleOptions} that carries every tunable property of a swap-hit particle. The full spawn
/// spec (tier physics, resolved colour, rolled lifetime) rides with the particle instead of a shared static, so a
/// {@link SwapParticleProvider} builds a particle purely from its options + sprite + per-particle jitter.
public final class SwapParticleOptions implements ParticleOptions
{
    private final ParticleType<SwapParticleOptions> type;
    private final int rgb;
    private final float baseSize;
    private final float gravity;
    private final float friction;
    /// Half-width of the per-particle velocity jitter added on top of the carried impulse.
    private final float speed;
    private final float upBias;
    private final int lifetime;

    public SwapParticleOptions(ParticleType<SwapParticleOptions> type, int rgb, float baseSize, float gravity,
                               float friction, float speed, float upBias, int lifetime)
    {
        this.type = type;
        this.rgb = rgb;
        this.baseSize = baseSize;
        this.gravity = gravity;
        this.friction = friction;
        this.speed = speed;
        this.upBias = upBias;
        this.lifetime = lifetime;
    }

    /* PRESETS — per-tier physics live here; colour + lifetime are supplied per spawn. */

    public static SwapParticleOptions possible(ParticleType<SwapParticleOptions> type, int rgb, int lifetime)
    {
        return new SwapParticleOptions(type, rgb, 0.15f, 0.012f, 0.92f, 0.05f, 0.045f, lifetime);
    }

    public static SwapParticleOptions attacked(ParticleType<SwapParticleOptions> type, int rgb, int lifetime)
    {
        return new SwapParticleOptions(type, rgb, 0.12f, 0.18f, 0.70f, 0.22f, 0.10f, lifetime);
    }

    public static SwapParticleOptions consecutive(ParticleType<SwapParticleOptions> type, int rgb, int lifetime)
    {
        return new SwapParticleOptions(type, rgb, 0.15f, 0.16f, 0.72f, 0.28f, 0.14f, lifetime);
    }

    /* CODECS */

    public static MapCodec<SwapParticleOptions> codec(ParticleType<SwapParticleOptions> type)
    {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("rgb").forGetter(o -> o.rgb),
                Codec.FLOAT.fieldOf("baseSize").forGetter(o -> o.baseSize),
                Codec.FLOAT.fieldOf("gravity").forGetter(o -> o.gravity),
                Codec.FLOAT.fieldOf("friction").forGetter(o -> o.friction),
                Codec.FLOAT.fieldOf("speed").forGetter(o -> o.speed),
                Codec.FLOAT.fieldOf("upBias").forGetter(o -> o.upBias),
                Codec.INT.fieldOf("lifetime").forGetter(o -> o.lifetime)
        ).apply(instance, (rgb, baseSize, gravity, friction, speed, upBias, lifetime) ->
                new SwapParticleOptions(type, rgb, baseSize, gravity, friction, speed, upBias, lifetime)));
    }

    public static StreamCodec<? super ByteBuf, SwapParticleOptions> streamCodec(ParticleType<SwapParticleOptions> type)
    {
        return StreamCodec.composite(
                ByteBufCodecs.VAR_INT, o -> o.rgb,
                ByteBufCodecs.FLOAT, o -> o.baseSize,
                ByteBufCodecs.FLOAT, o -> o.gravity,
                ByteBufCodecs.FLOAT, o -> o.friction,
                ByteBufCodecs.FLOAT, o -> o.speed,
                ByteBufCodecs.FLOAT, o -> o.upBias,
                ByteBufCodecs.VAR_INT, o -> o.lifetime,
                (rgb, baseSize, gravity, friction, speed, upBias, lifetime) ->
                        new SwapParticleOptions(type, rgb, baseSize, gravity, friction, speed, upBias, lifetime)
        );
    }

    /* GETTERS */

    @Override
    public ParticleType<SwapParticleOptions> getType() { return this.type; }

    public int rgb() { return this.rgb; }

    public float baseSize() { return this.baseSize; }

    public float gravity() { return this.gravity; }

    public float friction() { return this.friction; }

    public float speed() { return this.speed; }

    public float upBias() { return this.upBias; }

    public int lifetime() { return this.lifetime; }
}
