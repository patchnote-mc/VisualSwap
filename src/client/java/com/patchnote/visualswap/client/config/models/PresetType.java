package com.patchnote.visualswap.client.config.models;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/// Identity of a configurable preset plus its fixed defaults. The *mutable* per-preset values (size, From/To colors)
/// live on the {@link Preset} data class so they actually serialize — an enum would persist only its name. Only the
/// Custom preset is user-editable; Vanilla/Practice always show these defaults.
public enum PresetType
{
    VANILLA(0.8, 0x35FFFFFF, 0x95FFFFFF, 0xFFFFFFFF),
    PRACTICE(1.5, 0xFFFEA82F, 0xFFFF2E00, 0xFFFCFFF7),
    CUSTOM(1.5, 0xFF0B0014, 0xFFF5E9E2, 0xFF0B0014);

    private final double size;
    private final int fromColor;
    private final int toColor;
    private final int flashTint;

    PresetType(double size, int fromColor, int toColor, int flashTint)
    {
        this.size = size;
        this.fromColor = fromColor;
        this.toColor = toColor;
        this.flashTint = flashTint;
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

    /* HELPERS */

    public Component getNameComponent()
    {
        return Component.translatable("gui.visual-swap.preset." + name().toLowerCase(Locale.ROOT));
    }

    public Preset createDefault() { return new Preset(this.size, this.fromColor, this.toColor); }
}
