package com.patchnote.visualswap.client.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/// Item-icon helpers for the config screen: resolving an id string to an item, and building a render-safe
/// {@link ItemStack} even before item components bind (e.g. on the title screen), where {@code new ItemStack(item)}
/// would throw ("Components not bound yet").
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

    /// A render-safe stack for {@code item}: empty for AIR, a normal stack once components are bound, else a model-only
    /// stack that can still be drawn before a world loads.
    public static ItemStack stackFor(Item item)
    {
        if (item == Items.AIR) return ItemStack.EMPTY;

        // if item model already loaded
        if (BuiltInRegistries.ITEM.wrapAsHolder(item).areComponentsBound()) return new ItemStack(item);

        // load item model only
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        DataComponentMap components = DataComponentMap.builder().set(DataComponents.ITEM_MODEL, id).build();
        return new ItemStack(Holder.direct(item, components));
    }
}
