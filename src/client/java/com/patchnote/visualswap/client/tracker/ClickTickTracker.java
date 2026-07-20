package com.patchnote.visualswap.client.tracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public final class ClickTickTracker
{
    public static final ClickTickTracker INSTANCE = new ClickTickTracker();

    private ClickTickTracker() { }

    private ClickTickState previous = ClickTickState.EMPTY;
    private ClickTickState current = ClickTickState.EMPTY;

    /* EVENTS */

    public void eventClientTickStart(Minecraft client) { current = capture(client); }

    public void eventClientTickEnd() { previous = current; }

    public void eventReset()
    {
        this.current = ClickTickState.EMPTY;
        this.previous = ClickTickState.EMPTY;
        HotbarSelectSignal.clear();
    }

    /* STATE & QUERIES */

    public boolean attacked()
    {
        // swing check for spear
        return (current.swinging() && (!previous.swinging() || current.swingTime() < previous.swingTime())) ||
                current.attackDown() && !previous.attackDown() // left click
                || current.useDown() && !previous.useDown(); // right click
    }

    /* GETTERS */

    public static ClickTickState getCurrentState() { return INSTANCE.current; }

    public static ClickTickState getPreviousState() { return INSTANCE.previous; }

    /* HELPERS */

    private static ClickTickState capture(Minecraft client)
    {
        // Read-and-clear every tick so the latch never leaks into a later tick, even the tick we bail out as EMPTY.
        boolean slotSelected = HotbarSelectSignal.consume();
        LocalPlayer player = client.player;
        if (player == null) return ClickTickState.EMPTY;
        // get item; prefer not copying the item
        ItemStack previousItem = getPreviousState().mainHand();
        ItemStack mainHand;
        if (!ItemStack.isSameItem(previousItem, player.getMainHandItem())) mainHand = player.getMainHandItem().copy();
        else mainHand = previousItem;
        return new ClickTickState(
                player.tickCount,
                true,
                mainHand,
                player.getInventory().getSelectedSlot(),
                client.options.keyAttack.isDown(),
                client.options.keyUse.isDown(),
                player.swinging,
                player.swingTime,
                // get item cooldown
                player.getAttackStrengthScale(0.0f), // param (0.0f) for exact last tick
                mainHand.get(DataComponents.PIERCING_WEAPON) != null,
                slotSelected
        );
    }
}