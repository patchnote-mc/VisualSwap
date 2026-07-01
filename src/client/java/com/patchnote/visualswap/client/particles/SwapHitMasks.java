package com.patchnote.visualswap.client.particles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.ModConfig;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SwapHitMasks
{
    private SwapHitMasks() { }

    public static final String RESOURCE = "/assets/" + VisualSwap.MOD_ID + "/swap_hit_masks.json";

    /// @return the possible swap-hit mask.
    public static Mask possible() { return load("possible"); }

    /// @return the attacked / chained swap-hit mask.
    public static Mask attacked() { return load("attacked"); }

    /// @return the failed mask
    public static Mask failed() { return load("failed"); }

    /// @return the consecutive mask
    public static Mask consecutive() { return load("consecutive"); }

    /// Load and parse a named mask from {@code swap_hit_masks.json}
    ///
    /// @throws IllegalStateException if the resource or the named mask is missing/malformed.
    public static Mask load(String name)
    {
        try (InputStream in = SwapHitMasks.class.getResourceAsStream(RESOURCE))
        {
            if (in == null)
            {
                throw new IllegalStateException("Missing swap-hit mask resource: " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                                        .getAsJsonObject();
            JsonObject masks = root.getAsJsonObject("masks");
            if (masks == null || !masks.has(name))
            {
                throw new IllegalStateException("Mask '" + name + "' not found in " + RESOURCE);
            }
            return parse(masks.getAsJsonObject(name));
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to load swap-hit mask '" + name + "'", e);
        }
    }

    private static Mask parse(JsonObject mask)
    {
        String particle = mask.get("particle").getAsString();
        JsonObject color = mask.getAsJsonObject("color");
        JsonObject particleColor = mask.getAsJsonObject("particleColor");
        List<String> rows = mask.getAsJsonArray("rows").asList().stream().map(JsonElement::getAsString).toList();
        return new Mask(
                particle,
                parseArgb(color.get("vanilla").getAsString()),
                parseArgb(color.get("practice").getAsString()),
                parseArgb(particleColor.get("vanilla").getAsString()),
                parseArgb(particleColor.get("practice").getAsString()),
                rows
        );
    }

    private static int parseArgb(String hex)
    {
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        return (int) Long.parseLong(s, 16);
    }

    /* HELPERS */

    public record Mask(String particle, int colorVanilla, int colorPractice, int particleColorVanilla,
                       int particleColorPractice, List<String> rows)
    {
        /// @return the ARGB HUD tint for the active {@link ModConfig.IndicatorType}.
        public int color() { return selectColor(this.colorVanilla, this.colorPractice); }

        /// @return the ARGB particle tint for the active {@link ModConfig.IndicatorType}.
        public int particleColor() { return selectColor(this.particleColorVanilla, this.particleColorPractice); }

        private static int selectColor(int vanilla, int practice)
        {
            return ModConfig.get().indicatorType == ModConfig.IndicatorType.PRACTICE ? practice : vanilla;
        }

        public int width() { return rows.isEmpty() ? 0 : rows.getFirst().length(); }

        public int height() { return rows.size(); }

        public boolean filled(int col, int row) { return rows.get(row).charAt(col) == '#'; }

        public boolean canDraw() { return this.height() != 0; }
    }
}
