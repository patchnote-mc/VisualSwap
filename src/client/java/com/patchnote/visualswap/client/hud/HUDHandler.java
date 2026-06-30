package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.VisualSwap;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

public class HUDHandler
{
    public static final SwapHitGlyph GLYPH = new SwapHitGlyph();

    private HUDHandler() { }

    public static void register()
    {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, "swap_hit_glyph"),
                GLYPH
        );
    }
}
