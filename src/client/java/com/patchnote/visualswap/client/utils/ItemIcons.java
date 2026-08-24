package com.patchnote.visualswap.client.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/// Item-icon helpers for the config screen: resolving an id string to an item and building its preview stack.
public final class ItemIcons
{
    private ItemIcons() { }

    /// The item for {@code id} (namespace defaulted), or {@link Items#AIR} when blank / unparseable / unknown. Safe
    /// before item components bind (it only touches the registry keys).
    public static Item resolveItem(String id)
    {
        Identifier ident = Identifier.tryParse(id == null ? "" : id.trim());
        if (ident == null) return Items.AIR;
        return BuiltInRegistries.ITEM.getValue(ident);
    }

    /// A render-safe stack for {@code item}, or empty for AIR.
    public static ItemStack stackFor(Item item)
    {
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }
}
