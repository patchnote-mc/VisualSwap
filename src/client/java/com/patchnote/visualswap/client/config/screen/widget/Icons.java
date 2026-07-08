package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.VisualSwap;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/// The config screen's button icons — Material Symbols glyphs rasterized to white 128×128 PNGs by `.gen/gen_icons.py`
/// (a sibling `.png.mcmeta` enables linear filtering so they downscale smoothly). Blitted tinted per state via
/// {@link #blit}; see {@link IconButton}.
public final class Icons
{
    private Icons() { }

    /// Source texture resolution (each PNG is TEX×TEX). The art is drawn much smaller and scaled down at blit time.
    private static final int TEX = 128;

    public static final Identifier ADD = of("add");
    public static final Identifier DELETE = of("delete");
    public static final Identifier DUPLICATE = of("duplicate");
    public static final Identifier RESET = of("reset");
    public static final Identifier CLEAR = of("clear");
    public static final Identifier SEARCH = of("search");
    public static final Identifier DIRTY = of("dirty");
    public static final Identifier CHECK = of("check");
    public static final Identifier MOVE_UP = of("move_up");
    public static final Identifier MOVE_DOWN = of("move_down");

    /// Draw {@code icon} as a {@code size}×{@code size} square at ({@code x},{@code y}), multiply-tinted by {@code argb}
    /// (the art is white, so the tint sets its colour). The 128px source is scaled down with linear filtering.
    public static void blit(GuiGraphicsExtractor g, Identifier icon, int x, int y, int size, int argb)
    {
        g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0f, 0.0f, size, size, TEX, TEX, TEX, TEX, argb);
    }

    private static Identifier of(String name)
    {
        return Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, "textures/gui/icons/" + name + ".png");
    }
}
