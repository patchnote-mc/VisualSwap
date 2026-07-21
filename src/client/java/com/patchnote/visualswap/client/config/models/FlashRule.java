package com.patchnote.visualswap.client.config.models;

import com.patchnote.visualswap.client.utils.ItemRegex;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/// A regex selector over item ids plus when/how a matched item flashes, and the tint colour it flashes under each
/// {@link PresetType}. The selector ({@link #item}) is matched against each item id with
/// {@link java.util.regex.Matcher#find()} (substring), so a full literal id matches only itself while a partial pattern
/// (e.g. {@code _sword}) bulk-selects. Individual ids the pattern would otherwise catch can be dropped via
/// {@link #excludedItems}.
public final class FlashRule
{
    private String item;
    private FlashTrigger flashesAt;
    private FlashIntensity intensity;

    /// Whether switching to an item this rule selects shows the on-screen swap-hit indicators — the below-hotbar glyph
    /// *and* the hotbar slot highlight, collapsed under this one flag.
    private boolean showSwapEffects;

    /// Precedence key: the runtime resolves rules in ascending {@code order} and the first whose selector matches a
    /// held item (per input) wins. Stamped from the rule's list position when the config is committed/loaded.
    private int order;

    /// Item ids to drop from this rule's matches even though the pattern catches them (e.g. keep {@code minecraft:cod}
    /// but not {@code minecraft:cod_bucket}). Toggled from the preview modal so the pattern text stays human-readable.
    private Set<String> excludedItems = new LinkedHashSet<>();

    /// One tint colour per preset (packed ARGB; only the RGB is used at render — the alpha byte carries the flash
    /// gamma). Only the Custom slot is user-editable; the others always resolve to {@link PresetType#getFlashTint()}
    /// (see {@link #colorFor}).
    private Map<PresetType, Integer> colors;

    /// The saved-snapshot rule this working copy descends from, or null when the rule was newly added (or duplicated)
    /// this editing session. Drives the per-row unsaved-changes marker (see {@link #isNew}/{@link #isModified}).
    /// Transient: an in-memory editing link between working copies that must never reach the persisted config.
    private transient FlashRule savedOrigin;

    /// Lazily-compiled {@link #item} pattern (null when blank/invalid), reset whenever {@link #item} changes.
    /// Transient: derived matching state that must never be serialised.
    private transient Pattern pattern;
    private transient boolean patternComputed;

    public FlashRule(String item, FlashTrigger flashesAt, FlashIntensity intensity, boolean showSwapEffects)
    {
        this.item = item;
        this.flashesAt = flashesAt;
        this.intensity = intensity;
        this.showSwapEffects = showSwapEffects;
        this.colors = defaultColors();
    }

    /// Deep copy — the working copies the config screen edits must not share the colour map or exclusion set with the
    /// committed rule.
    public FlashRule(FlashRule other)
    {
        this.item = other.item;
        this.flashesAt = other.flashesAt;
        this.intensity = other.intensity;
        this.showSwapEffects = other.showSwapEffects;
        this.order = other.order;
        this.excludedItems = new LinkedHashSet<>(other.excludedItems);
        this.colors = copyColors(other.colors);
        this.savedOrigin = other.savedOrigin;
    }

    /* GETTERS & SETTERS */

    public String item() { return item; }

    public FlashTrigger flashesAt() { return flashesAt; }

    public FlashIntensity intensity() { return intensity; }

    public boolean showSwapEffects() { return showSwapEffects; }

    public int order() { return order; }

    /// The tint colour to use under {@code preset}. Non-editable presets always resolve to their current default, so
    /// changing a default never leaves a stale stored value behind.
    public int colorFor(PresetType preset)
    {
        if (!preset.isColorEditable()) return preset.getFlashTint();
        Integer c = (colors != null) ? colors.get(preset) : null;
        return (c != null) ? c : preset.getFlashTint();
    }

    public FlashRule setItem(String item)
    {
        this.item = item;
        this.pattern = null;
        this.patternComputed = false;
        return this;
    }

    public FlashRule setFlashesAt(FlashTrigger flashesAt)
    {
        this.flashesAt = flashesAt;
        return this;
    }

    public FlashRule setIntensity(FlashIntensity intensity)
    {
        this.intensity = intensity;
        return this;
    }

    public FlashRule setShowSwapEffects(boolean showSwapEffects)
    {
        this.showSwapEffects = showSwapEffects;
        return this;
    }

    public FlashRule setOrder(int order)
    {
        this.order = order;
        return this;
    }

    /// The saved-snapshot rule this working copy descends from, or null if it was added this session.
    public FlashRule savedOrigin() { return savedOrigin; }

    /// Links this working copy to the saved rule it descends from (null marks it as newly added). Returns
    /// {@code this}.
    public FlashRule setSavedOrigin(FlashRule origin)
    {
        this.savedOrigin = origin;
        return this;
    }

    /// True when this rule was added this editing session (no saved counterpart) — drives the green per-row marker.
    public boolean isNew() { return savedOrigin == null; }

    /// True when this rule descends from a saved rule but its values now differ — drives the orange per-row marker.
    public boolean isModified() { return savedOrigin != null && !sameValuesAs(savedOrigin); }

    /// Sets the tint for {@code preset}. No-op for non-editable presets (only Custom is user-editable).
    public FlashRule setColorFor(PresetType preset, int color)
    {
        if (!preset.isColorEditable()) return this;
        ensureColors();
        colors.put(preset, color);
        return this;
    }

    /* PATTERN / MATCHING */

    /// The compiled selector, or null when {@link #item} is blank or not a valid regex.
    public @Nullable Pattern pattern()
    {
        if (!this.patternComputed)
        {
            this.pattern = ItemRegex.tryCompile(this.item);
            this.patternComputed = true;
        }
        return this.pattern;
    }

    /// Whether this rule selects item id {@code id}: its pattern is valid and finds within the id, and the id is not
    /// individually excluded.
    public boolean matches(String id)
    {
        Pattern p = pattern();
        return p != null && p.matcher(id).find() && !isExcluded(id);
    }

    /* EXCLUSIONS */

    public boolean isExcluded(String id) { return this.excludedItems.contains(id); }

    public void addExcluded(String id) { this.excludedItems.add(id); }

    public void removeExcluded(String id) { this.excludedItems.remove(id); }

    public Set<String> excludedItems() { return this.excludedItems; }

    /* HELPERS */

    /// Repairs a rule loaded from an older/partial config: Gson bypasses field initializers, so a pre-intensity /
    /// pre-colours / pre-exclusions schema deserialises with null/absent fields (and any concrete map/set type).
    public void normalize()
    {
        if (this.flashesAt == null) this.flashesAt = FlashTrigger.BOTH;
        if (this.intensity == null) this.intensity = FlashIntensity.LOW;
        if (this.excludedItems == null) this.excludedItems = new LinkedHashSet<>();
        ensureColors();
    }

    private void ensureColors()
    {
        this.colors = copyColors(this.colors);
    }

    /// Rebuilds {@code src} into a complete {@link EnumMap} — preserves any present entries and fills every missing
    /// preset with its default, so callers always get a full, well-typed map (Gson may hand back a plain LinkedHashMap
    /// or null).
    private static Map<PresetType, Integer> copyColors(Map<PresetType, Integer> src)
    {
        EnumMap<PresetType, Integer> map = new EnumMap<>(PresetType.class);
        if (src != null)
        {
            // Skip null keys/values: Gson deserialises an unknown preset name (hand-edited or renamed) to a null key,
            // which EnumMap.putAll would NPE on.
            for (Map.Entry<PresetType, Integer> e : src.entrySet())
                if (e.getKey() != null && e.getValue() != null) map.put(e.getKey(), e.getValue());
        }
        for (PresetType p : PresetType.values()) map.putIfAbsent(p, p.getFlashTint());
        return map;
    }

    /// Value equality of a rule's settings — item, trigger, intensity, exclusions, and the per-preset tints. Used to
    /// detect unsaved edits; deliberately NOT {@code equals}, because the screen matches rules by identity (e.g. the
    /// preview target). {@link #order} is intentionally excluded: it is re-stamped from list position on every
    /// structural edit, so including it would falsely mark unrelated rows "modified" — a genuine reorder of distinct
    /// rules is still caught positionally by {@link #listsSameValues}.
    public boolean sameValuesAs(FlashRule other)
    {
        if (other == null) return false;
        if (!Objects.equals(this.item, other.item)) return false;
        if (this.flashesAt != other.flashesAt) return false;
        if (this.intensity != other.intensity) return false;
        if (this.showSwapEffects != other.showSwapEffects) return false;
        if (!Objects.equals(this.excludedItems, other.excludedItems)) return false;
        for (PresetType p : PresetType.values())
            if (this.colorFor(p) != other.colorFor(p)) return false;
        return true;
    }

    /// True iff two rule lists are element-wise value-equal (order-sensitive) — see {@link #sameValuesAs}.
    public static boolean listsSameValues(List<FlashRule> a, List<FlashRule> b)
    {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++)
            if (!a.get(i).sameValuesAs(b.get(i))) return false;
        return true;
    }

    private static Map<PresetType, Integer> defaultColors()
    {
        EnumMap<PresetType, Integer> map = new EnumMap<>(PresetType.class);
        for (PresetType p : PresetType.values()) map.put(p, p.getFlashTint());
        return map;
    }

    /// Mutable — {@link com.patchnote.visualswap.client.config.ModConfig#validatePostLoad} sorts and re-stamps the list
    /// in place.
    public static List<FlashRule> defaultFlashRules()
    {
        List<FlashRule> rules = new ArrayList<>();
        addRule(rules, "_sword", FlashTrigger.ATTACK, FlashIntensity.HIGH, true);
        addRule(rules, "_axe", FlashTrigger.ATTACK, FlashIntensity.HIGH, true);
        addRule(rules, "_spear", FlashTrigger.ATTACK, FlashIntensity.HIGH, true);
        addRule(rules, "minecraft:mace", FlashTrigger.ATTACK, FlashIntensity.HIGH, true);
        addRule(rules, "minecraft:trident", FlashTrigger.ATTACK, FlashIntensity.LOW, true);
        addRule(rules, "minecraft:ender_pearl", FlashTrigger.USE, FlashIntensity.LOW, true);
        addRule(rules, "minecraft:wind_charge", FlashTrigger.USE, FlashIntensity.LOW, true);
        return rules;
    }

    private static void addRule(List<FlashRule> list, String item, FlashTrigger flashesAt, FlashIntensity intensity,
                                boolean showSwapEffects)
    {
        list.add(new FlashRule(item, flashesAt, intensity, showSwapEffects));
    }
}
