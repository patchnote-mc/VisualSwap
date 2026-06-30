package com.patchnote.visualswap.client.particles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.patchnote.visualswap.VisualSwap;

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
        int color = parseArgb(mask.get("color").getAsString());
        int particleColor = parseArgb(mask.get("particleColor").getAsString());
        List<String> rows = mask.getAsJsonArray("rows").asList().stream().map(JsonElement::getAsString).toList();
        return new Mask(particle, color, particleColor, rows);
    }

    private static int parseArgb(String hex)
    {
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        return (int) Long.parseLong(s, 16);
    }

    /* HELPERS */

    public record Mask(String particle, int color, int particleColor, List<String> rows)
    {
        public int width() { return rows.isEmpty() ? 0 : rows.getFirst().length(); }

        public int height() { return rows.size(); }

        public boolean filled(int col, int row) { return rows.get(row).charAt(col) == '#'; }

        public boolean canDraw() { return this.height() != 0; }
    }
}
