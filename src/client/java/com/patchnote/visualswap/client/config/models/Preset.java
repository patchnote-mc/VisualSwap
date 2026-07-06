package com.patchnote.visualswap.client.config.models;

public enum Preset
{
    VANILLA("Vanilla", 0.8f, 0x40FFFFFF, 0x95FFFFFF),
    PRACTICE("Practice", 1.0f, 0xFFFF0000, 0xFF00FF00),
    CUSTOM("Custom", 1.0f);

    private final String displayName;
    private double sizeMultiplier;
    private int fromColor;
    private int toColor;

    Preset(String displayName, float defaultSizeMultiplier)
    {
        this.sizeMultiplier = defaultSizeMultiplier;
        this.displayName = displayName;
    }

    Preset(String displayName, float defaultSizeMultiplier, int fromColor, int toColor)
    {
        this(displayName, defaultSizeMultiplier);
        this.fromColor = fromColor;
        this.toColor = toColor;
    }

    public boolean is(Preset other) { return this == other; }

    public boolean isVanilla() { return is(VANILLA); }

    public boolean isPractice() { return is(PRACTICE); }

    public boolean isCustom() { return is(CUSTOM); }

    /* GETTERS & SETTERS */

    public double getSizeMultiplier() { return sizeMultiplier; }

    public String getDisplayName() { return this.displayName; }

    public int getFromColor() { return fromColor; }

    public int getToColor() { return toColor; }

    public Preset setSizeMultiplier(double sizeMultiplier)
    {
        if (this.isCustom()) this.sizeMultiplier = sizeMultiplier;
        return this;
    }

    public Preset setFromColor(int fromColor)
    {
        if (this.isCustom()) this.fromColor = fromColor;
        return this;
    }

    public Preset setToColor(int toColor)
    {
        if (this.isCustom()) this.toColor = toColor;
        return this;
    }
}
