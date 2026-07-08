package com.patchnote.visualswap.client.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;

/// The in-world swap-hit GLYPH particle
public class SwapParticle extends SingleQuadParticle
{
    /// Fraction of lifetime over which alpha eases from 1 to 0 before the end
    private static final float FADE_FRACTION = 0.5f;

    SwapParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za,
                 TextureAtlasSprite sprite, int rgb, float baseSize, int lifetime, float gravity, float friction)
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

    /* OVERRIDES */

    @Override
    public void tick()
    {
        super.tick();
        float t = (float) this.age / (float) this.lifetime;
        float fadeStart = 1.0f - FADE_FRACTION;
        this.alpha = t <= fadeStart ? 1.0f : Mth.clamp(1.0f - (t - fadeStart) / FADE_FRACTION, 0.0f, 1.0f);
    }

    @Override
    public @NonNull Layer getLayer() { return Layer.TRANSLUCENT; }
}
