package com.patchnote.visualswap.client.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Regex matching of item identifiers against the item registry. A flash rule's selector is a regex matched with
/// {@link Matcher#find()} (substring), so {@code _sword} catches every sword while a full literal id catches only
/// itself. All matching is pure string work over {@code BuiltInRegistries.ITEM.keySet()}, so it is safe even before item
/// components bind (e.g. on the title screen).
public final class ItemRegex
{
    private ItemRegex() { }

    /// Compile {@code pattern}, or null when it is blank or not a valid regex.
    public static @Nullable Pattern tryCompile(@Nullable String pattern)
    {
        if (pattern == null || pattern.isBlank()) return null;
        try
        {
            return Pattern.compile(pattern);
        }
        catch (PatternSyntaxException e)
        {
            return null;
        }
    }

    /// How many item ids {@code pattern} matches plus a representative first match (registry-iteration order) — enough to
    /// drive a rule row's validity and preview icon without allocating the full id list. A null pattern is empty. Item
    /// exclusions are deliberately ignored here (they are a per-item refinement handled by the rule/modal); this reports
    /// the raw reach of the pattern itself.
    public static Summary summarize(@Nullable Pattern pattern)
    {
        if (pattern == null) return Summary.EMPTY;
        int count = 0;
        Identifier first = null;
        Matcher matcher = pattern.matcher("");
        for (Identifier id : BuiltInRegistries.ITEM.keySet())
        {
            if (matcher.reset(id.toString()).find())
            {
                count++;
                if (first == null) first = id;
            }
        }
        return new Summary(count, first);
    }

    /// Every item id {@code pattern} matches, sorted by id — the preview modal's full list. A null pattern is empty.
    public static List<Identifier> matchingIds(@Nullable Pattern pattern)
    {
        if (pattern == null) return List.of();
        List<Identifier> ids = new ArrayList<>();
        Matcher matcher = pattern.matcher("");
        for (Identifier id : BuiltInRegistries.ITEM.keySet())
            if (matcher.reset(id.toString()).find()) ids.add(id);
        ids.sort(Comparator.comparing(Identifier::toString));
        return ids;
    }

    /// The non-overlapping, non-empty match runs of {@code pattern} within {@code id}, flattened as {@code [start,end,…]}
    /// — the substrings the modal highlights as the reason the id was included. Empty when nothing matches.
    public static int[] spans(Pattern pattern, String id)
    {
        Matcher matcher = pattern.matcher(id);
        List<Integer> bounds = new ArrayList<>();
        while (matcher.find())   // find() auto-advances past zero-width matches, so this terminates
        {
            if (matcher.end() > matcher.start())
            {
                bounds.add(matcher.start());
                bounds.add(matcher.end());
            }
        }
        int[] out = new int[bounds.size()];
        for (int i = 0; i < out.length; i++) out[i] = bounds.get(i);
        return out;
    }

    /// A pattern's raw reach: the number of matching item ids and a representative first one (null when none).
    public record Summary(int count, @Nullable Identifier first)
    {
        static final Summary EMPTY = new Summary(0, null);
    }
}
