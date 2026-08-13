package com.patchnote.visualswap.client.hud.click;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.models.FlashRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.*;

/// Resolves which flash rule (if any) wins for a held item, per input, from the current config's rule list. Rules are
/// considered in ascending precedence {@link FlashRule#order()}; the first whose selector matches the held item's id
/// and that covers the input wins. Results are memoised per {@link Item} (misses included) so the tick path is O(1)
/// amortised and never re-runs a regex for an item it has already seen. The cached index is rebuilt only when the
/// config's rule list is replaced (a save/load assigns a fresh list). Render/client-thread use only.
final class FlashRuleIndex
{
    private static @Nullable FlashRuleIndex cached;

    private final List<FlashRule> source;   // identity key — a save/load assigns a fresh list instance
    private final List<FlashRule> byOrder;  // {@link #source} sorted by precedence
    private final Map<Item, FlashRule> attackWinners = new HashMap<>();
    private final Map<Item, FlashRule> useWinners = new HashMap<>();
    private final Map<Item, FlashRule> matchWinners = new HashMap<>();   // first-by-order match, ignoring input

    private FlashRuleIndex(List<FlashRule> source)
    {
        this.source = source;
        this.byOrder = new ArrayList<>(source);
        this.byOrder.sort(Comparator.comparingInt(FlashRule::order));
    }

    /// The index for the live config, rebuilt only when the rule list is replaced (compared by identity).
    static FlashRuleIndex forCurrentConfig()
    {
        List<FlashRule> rules = ModConfig.get().clickFlashRules;
        FlashRuleIndex index = cached;
        if (index == null || index.source != rules)
        {
            index = new FlashRuleIndex(rules);
            cached = index;
        }
        return index;
    }

    /// The winning rule for {@code item} on the given input, or null when none matches. Memoised, including misses.
    @Nullable FlashRule rule(Item item, boolean forAttack)
    {
        Map<Item, FlashRule> cache = forAttack ? this.attackWinners : this.useWinners;
        FlashRule winner = cache.get(item);
        if (winner != null) return winner;
        if (cache.containsKey(item)) return null;   // memoised miss

        winner = resolve(item, forAttack);
        cache.put(item, winner);   // stores null for a miss too, so it isn't recomputed
        return winner;
    }

    private @Nullable FlashRule resolve(Item item, boolean forAttack)
    {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) return null;
        String id = key.toString();
        for (FlashRule rule : this.byOrder)
        {
            if (rule == null || rule.flashesAt() == null) continue;
            boolean covers = forAttack ? rule.flashesAt().flashesOnAttack() : rule.flashesAt().flashesOnUse();
            if (covers && rule.matches(id)) return rule;
        }
        return null;
    }

    /// The highest-precedence rule whose selector matches {@code item}, regardless of the input it flashes on, or null.
    /// The swap indicators resolve per switched-to item this way, so trigger is irrelevant here. Memoised, misses
    /// included.
    @Nullable FlashRule matchingRule(Item item)
    {
        FlashRule winner = this.matchWinners.get(item);
        if (winner != null) return winner;
        if (this.matchWinners.containsKey(item)) return null;   // memoised miss

        winner = resolveMatch(item);
        this.matchWinners.put(item, winner);
        return winner;
    }

    private @Nullable FlashRule resolveMatch(Item item)
    {
        Identifier key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) return null;
        String id = key.toString();
        for (FlashRule rule : this.byOrder)
        {
            if (rule != null && rule.matches(id)) return rule;
        }
        return null;
    }
}
