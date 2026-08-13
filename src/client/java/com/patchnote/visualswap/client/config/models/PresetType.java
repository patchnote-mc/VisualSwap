package com.patchnote.visualswap.client.config.models;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/// Identity of a configurable preset plus its fixed defaults. The *mutable* per-preset values (size, From/To colors)
/// live on the {@link Preset} data class so they actually serialize — an enum would persist only its name. Only the
/// Custom preset is user-editable; Vanilla/Practice always show these defaults.
public enum PresetType
{
    VANILLA(
            0.8, 0x35FFFFFF, 0x95FFFFFF, 0xFFFFFFFF, //
            new GlyphColors(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF85A5A, 0xFFFFFFFF)
    ),
    PRACTICE(
            1.5, 0xFFFEA82F, 0xFFFF2E00, 0xFFFCFFF7, //
            new GlyphColors(0xFF6767FF, 0xFF00FF00, 0xFFFF0000, 0xFFFFAA00)
    ),
    CUSTOM(
            1.5, 0xA6DBDAEA, 0xA6DDC3D0, 0xFF9B2915, //
            new GlyphColors(0xFFC77DFF, 0xFF42E8E0, 0xFFFF4567, 0xFFFFC857)
    );

    private final double size;
    private final int fromColor;
    private final int toColor;
    private final int flashTint;
    private final GlyphColors glyphColors;

    PresetType(double size, int fromColor, int toColor, int flashTint, GlyphColors glyphColors)
    {
        this.size = size;
        this.fromColor = fromColor;
        this.toColor = toColor;
        this.flashTint = flashTint;
        this.glyphColors = glyphColors;
    }

    public boolean isVanilla() { return this == VANILLA; }

    public boolean isPractice() { return this == PRACTICE; }

    public boolean isCustom() { return this == CUSTOM; }

    /// Only the Custom preset's colors/size are user-editable; the others show fixed defaults (greyed out).
    public boolean isColorEditable() { return isCustom(); }

    /* GETTERS */

    public double getSize() { return this.size; }

    public int getFromColor() { return this.fromColor; }

    public int getToColor() { return this.toColor; }

    public int getFlashTint() { return this.flashTint; }

    public int getGlyphColor() { return this.glyphColors.possible(); }

    public int getGlyphColor(String glyph)
    {
        return switch (glyph)
        {
            case "attacked" -> this.glyphColors.attacked();
            case "failed" -> this.glyphColors.failed();
            case "consecutive" -> this.glyphColors.consecutive();
            default -> this.glyphColors.possible();
        };
    }

    /* HELPERS */

    public Component getNameComponent()
    {
        return Component.translatable("gui.visual-swap.preset." + name().toLowerCase(Locale.ROOT));
    }

    public Preset createDefault()
    {
        return new Preset(
                this.size,
                this.fromColor,
                this.toColor,
                this.glyphColors.possible(),
                this.glyphColors.attacked(),
                this.glyphColors.failed(),
                this.glyphColors.consecutive()
        );
    }

    private record GlyphColors(int possible, int attacked, int failed, int consecutive) { }
}
