package com.patchnote.visualswap.client.config.models;

/// A preset's mutable settings — size, From/To highlight colours, and per-state glyph colours. This is a plain data
/// object (not an enum) so its fields actually serialise; the identity and fixed defaults live on
/// {@link PresetType}. In
/// practice only the Custom preset's settings are edited and persisted (see {@code ModConfig.customPreset}).
public final class Preset
{
    private double sizeMultiplier;
    private int fromColor;
    private int toColor;
    /// Legacy/swap-possible colour. Kept under its original serialized name so existing configs migrate losslessly.
    private int glyphColor;
    private int attackedGlyphColor;
    private int failedGlyphColor;
    private int consecutiveGlyphColor;

    public Preset() { }  // for the (de)serializer

    public Preset(double sizeMultiplier, int fromColor, int toColor, int glyphColor)
    {
        this(sizeMultiplier, fromColor, toColor, glyphColor, glyphColor, glyphColor, glyphColor);
    }

    public Preset(double sizeMultiplier, int fromColor, int toColor, int glyphColor, int attackedGlyphColor,
                  int failedGlyphColor, int consecutiveGlyphColor)
    {
        this.sizeMultiplier = sizeMultiplier;
        this.fromColor = fromColor;
        this.toColor = toColor;
        this.glyphColor = glyphColor;
        this.attackedGlyphColor = attackedGlyphColor;
        this.failedGlyphColor = failedGlyphColor;
        this.consecutiveGlyphColor = consecutiveGlyphColor;
    }

    public Preset(Preset other)
    {
        this(
                other.sizeMultiplier,
                other.fromColor,
                other.toColor,
                other.glyphColor,
                other.attackedGlyphColor,
                other.failedGlyphColor,
                other.consecutiveGlyphColor
        );
    }

    /* GETTERS & SETTERS */

    public double getSizeMultiplier() { return sizeMultiplier; }

    public int getFromColor() { return fromColor; }

    public int getToColor() { return toColor; }

    public int getGlyphColor() { return glyphColor; }

    public int getGlyphColor(String glyph)
    {
        return switch (glyph)
        {
            case "attacked" -> this.attackedGlyphColor;
            case "failed" -> this.failedGlyphColor;
            case "consecutive" -> this.consecutiveGlyphColor;
            default -> this.glyphColor;
        };
    }

    public Preset setSizeMultiplier(double sizeMultiplier)
    {
        this.sizeMultiplier = sizeMultiplier;
        return this;
    }

    public Preset setFromColor(int fromColor)
    {
        this.fromColor = fromColor;
        return this;
    }

    public Preset setToColor(int toColor)
    {
        this.toColor = toColor;
        return this;
    }

    public Preset setGlyphColor(int glyphColor)
    {
        this.glyphColor = glyphColor;
        return this;
    }

    public Preset setGlyphColor(String glyph, int glyphColor)
    {
        switch (glyph)
        {
            case "attacked" -> this.attackedGlyphColor = glyphColor;
            case "failed" -> this.failedGlyphColor = glyphColor;
            case "consecutive" -> this.consecutiveGlyphColor = glyphColor;
            default -> this.glyphColor = glyphColor;
        }
        return this;
    }

    /// Populate fields introduced with per-glyph colours from the legacy shared glyph colour.
    public void migrateGlyphColors(int fallback)
    {
        if (this.glyphColor == 0) this.glyphColor = fallback;
        if (this.attackedGlyphColor == 0) this.attackedGlyphColor = this.glyphColor;
        if (this.failedGlyphColor == 0) this.failedGlyphColor = this.glyphColor;
        if (this.consecutiveGlyphColor == 0) this.consecutiveGlyphColor = this.glyphColor;
    }

    /* COMPARISON */

    /// Value equality of the editable settings — used to detect unsaved edits without overriding {@code equals} (which
    /// some callers rely on being identity-based).
    public boolean sameValuesAs(Preset other)
    {
        return other != null
                && Double.compare(this.sizeMultiplier, other.sizeMultiplier) == 0
                && this.fromColor == other.fromColor
                && this.toColor == other.toColor
                && this.glyphColor == other.glyphColor
                && this.attackedGlyphColor == other.attackedGlyphColor
                && this.failedGlyphColor == other.failedGlyphColor
                && this.consecutiveGlyphColor == other.consecutiveGlyphColor;
    }
}
