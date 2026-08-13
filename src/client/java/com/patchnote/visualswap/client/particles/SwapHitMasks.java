package com.patchnote.visualswap.client.particles;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.ModConfig;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SwapHitMasks
{
    private SwapHitMasks() { }

    public static final String RESOURCE = "/assets/" + VisualSwap.MOD_ID + "/swap_hit_masks.json";

    /// Parsed masks, loaded once from {@link #RESOURCE}. HUD colours resolve through the active preset; masks retain
    /// only their geometry and particle colour variants.
    private static Map<String, Mask> cache;

    /* MASKS */

    /// @return the possible swap-hit mask.
    public static Mask possible() { return get("possible"); }

    /// @return the attacked / chained swap-hit mask.
    public static Mask attacked() { return get("attacked"); }

    /// @return the failed mask
    public static Mask failed() { return get("failed"); }

    /// @return the consecutive mask
    public static Mask consecutive() { return get("consecutive"); }

    /* HELPERS */

    private static Mask get(String name)
    {
        Mask mask = masks().get(name);
        if (mask == null)
        {
            throw new IllegalStateException("Mask '" + name + "' not found in " + RESOURCE);
        }
        return mask;
    }

    private static synchronized Map<String, Mask> masks()
    {
        if (cache == null) cache = loadAll();
        return cache;
    }

    /// Load and parse every mask from {@code swap_hit_masks.json} once.
    ///
    /// @throws IllegalStateException if the resource is missing or unreadable; JSON/structure errors propagate with
    /// their own message rather than being flattened into a generic one.
    private static Map<String, Mask> loadAll()
    {
        JsonObject root;
        try (InputStream in = SwapHitMasks.class.getResourceAsStream(RESOURCE))
        {
            if (in == null)
            {
                throw new IllegalStateException("Missing swap-hit mask resource: " + RESOURCE);
            }
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                             .getAsJsonObject();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Failed to read swap-hit mask resource " + RESOURCE, e);
        }

        JsonObject masks = root.getAsJsonObject("masks");
        if (masks == null)
        {
            throw new IllegalStateException("No 'masks' object in " + RESOURCE);
        }
        Map<String, Mask> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : masks.entrySet())
        {
            result.put(entry.getKey(), parse(entry.getKey(), entry.getValue().getAsJsonObject()));
        }
        return Map.copyOf(result);
    }

    private static Mask parse(String name, JsonObject mask)
    {
        // HUD-only masks (e.g. 'failed') omit 'particle' — no particle type is registered for them.
        String particle = mask.has("particle") ? mask.get("particle").getAsString() : null;
        JsonObject particleColor = mask.getAsJsonObject("particleColor");
        List<String> rows = mask.getAsJsonArray("rows")
                                .asList()
                                .stream()
                                .map(JsonElement::getAsString)
                                .toList();
        return new Mask(
                name,
                particle,
                parseArgb(particleColor.get("vanilla")
                                       .getAsString()),
                parseArgb(particleColor.get("practice")
                                       .getAsString()),
                rows
        );
    }

    private static int parseArgb(String hex)
    {
        String s = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        return (int) Long.parseLong(s, 16);
    }

    /* RECORDS */

    public record Mask(String name, @Nullable String particle, int particleColorVanilla, int particleColorPractice,
                       List<String> rows)
    {
        /// @return the ARGB HUD tint for the active preset.
        public int color() { return ModConfig.get().getGlyphColor(this.name); }

        /// @return the ARGB particle tint for the active preset.
        public int particleColor()
        {
            ModConfig cfg = ModConfig.get();
            if (cfg.preset.isCustom()) return "failed".equals(this.name) ? cfg.getFromColor() : cfg.getToColor();
            return cfg.preset.isPractice() ? this.particleColorPractice : this.particleColorVanilla;
        }

        public int width()
        {
            return rows.isEmpty()
                   ? 0
                   : rows.getFirst()
                         .length();
        }

        public int height() { return rows.size(); }

        public boolean filled(int col, int row)
        {
            return rows.get(row)
                       .charAt(col) == '#';
        }

        public boolean canDraw() { return this.height() != 0; }
    }
}
