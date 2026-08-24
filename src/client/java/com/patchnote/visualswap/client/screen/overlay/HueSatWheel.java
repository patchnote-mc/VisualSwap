package com.patchnote.visualswap.client.screen.overlay;

import com.mojang.blaze3d.platform.NativeImage;
import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.utils.ColorHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

/// The classic HSV pinwheel: hue around the ring, saturation from centre to rim. The wheel image (value = 1) is baked
/// once into a {@link DynamicTexture} and tinted grey at draw time to show the current value; a small cursor marks the
/// current hue/saturation. Click or drag picks.
final class HueSatWheel extends AbstractWidget
{
    /// Baked at the widget's on-screen size — GUI scaling upsamples it like any other chunky GUI art.
    static final int SIZE = 72;

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, "hue_sat_wheel");
    private static boolean textureRegistered;

    @FunctionalInterface
    interface HueSatConsumer
    {
        void accept(float hue, float sat);
    }

    /// Current {@code [hue, sat, value]} — re-read each frame so the cursor and value dimming track live edits.
    private final Supplier<float[]> hsv;
    private final HueSatConsumer onPick;

    HueSatWheel(int x, int y, Supplier<float[]> hsv, HueSatConsumer onPick)
    {
        super(x, y, SIZE, SIZE, Component.empty());
        this.hsv = hsv;
        this.onPick = onPick;
        ensureTexture();
    }

    /// Bake the value=1 wheel once per session; TextureManager.register replaces safely if ever re-run.
    private static void ensureTexture()
    {
        if (textureRegistered) return;

        NativeImage image = new NativeImage(SIZE, SIZE, true);
        float center = (SIZE - 1) / 2f;
        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                if (r > 1f) continue;  // stays transparent

                float hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360.0) % 360.0);
                // opaque disk with a ~1px anti-aliased rim: distance-in-texels from the edge, scaled to a full byte
                int alpha = Math.clamp(Math.round((1f - r) * center * 255f), 0, 255);
                image.setPixel(x, y, ColorHelpers.hsvToArgb(hue, Math.min(r, 1f), 1f, alpha));
            }
        }
        // DynamicTexture takes ownership of the image and uploads in its constructor
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(TEXTURE::toString, image));
        textureRegistered = true;
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float a)
    {
        float[] hsv = this.hsv.get();

        // the baked wheel is value=1; multiplying by grey darkens every pixel to the current value
        int v = Math.clamp(Math.round(hsv[2] * 255f), 0, 255);
        int valueTint = 0xFF000000 | (v << 16) | (v << 8) | v;
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, getX(), getY(), 0f, 0f, SIZE, SIZE, SIZE, SIZE, valueTint);

        // cursor at the current hue/sat
        float radius = SIZE / 2f;
        int cx = getX() + Math.round(radius + (float) Math.cos(Math.toRadians(hsv[0])) * hsv[1] * (radius - 2));
        int cy = getY() + Math.round(radius + (float) Math.sin(Math.toRadians(hsv[0])) * hsv[1] * (radius - 2));
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF202024);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFF0F0F0);
        g.fill(cx, cy, cx + 1, cy + 1, ColorHelpers.hsvToArgb(hsv[0], hsv[1], hsv[2], 255));
    }

    /* INPUT */

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) { pick(event.x(), event.y()); }

    @Override
    protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) { pick(event.x(), event.y()); }

    private void pick(double mouseX, double mouseY)
    {
        float radius = SIZE / 2f;
        double dx = mouseX - (getX() + radius);
        double dy = mouseY - (getY() + radius);
        float hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360.0) % 360.0);
        float sat = (float) Math.min(Math.sqrt(dx * dx + dy * dy) / (radius - 2), 1.0);
        this.onPick.accept(hue, sat);
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) { }  // continuous control — no click per grab

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}
