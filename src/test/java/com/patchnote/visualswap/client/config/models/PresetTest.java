package com.patchnote.visualswap.client.config.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class PresetTest
{
    @Test
    void migratesLegacySharedColorToEveryGlyph()
    {
        Preset preset = new Preset();
        preset.setGlyphColor(0xFF123456);

        preset.migrateGlyphColors(0xFFFFFFFF);

        assertEquals(0xFF123456, preset.getGlyphColor("possible"));
        assertEquals(0xFF123456, preset.getGlyphColor("attacked"));
        assertEquals(0xFF123456, preset.getGlyphColor("failed"));
        assertEquals(0xFF123456, preset.getGlyphColor("consecutive"));
    }

    @Test
    void individualGlyphColorsParticipateInValueComparison()
    {
        Preset original = new Preset(1.5, 1, 2, 3);
        Preset edited = new Preset(original).setGlyphColor("attacked", 4);

        assertFalse(original.sameValuesAs(edited));
        assertEquals(3, original.getGlyphColor("attacked"));
        assertEquals(4, edited.getGlyphColor("attacked"));
    }

    @Test
    void presetTypeOwnsEveryFixedGlyphColor()
    {
        assertEquals(0xFFFFFFFF, PresetType.VANILLA.getGlyphColor("possible"));
        assertEquals(0xFFF85A5A, PresetType.VANILLA.getGlyphColor("failed"));
        assertEquals(0xFF6767FF, PresetType.PRACTICE.getGlyphColor("possible"));
        assertEquals(0xFF00FF00, PresetType.PRACTICE.getGlyphColor("attacked"));
        assertEquals(0xFFFF0000, PresetType.PRACTICE.getGlyphColor("failed"));
        assertEquals(0xFFFFAA00, PresetType.PRACTICE.getGlyphColor("consecutive"));

        Preset custom = PresetType.CUSTOM.createDefault();
        assertEquals(PresetType.CUSTOM.getGlyphColor("possible"), custom.getGlyphColor("possible"));
        assertEquals(PresetType.CUSTOM.getGlyphColor("attacked"), custom.getGlyphColor("attacked"));
        assertEquals(PresetType.CUSTOM.getGlyphColor("failed"), custom.getGlyphColor("failed"));
        assertEquals(PresetType.CUSTOM.getGlyphColor("consecutive"), custom.getGlyphColor("consecutive"));
    }
}
