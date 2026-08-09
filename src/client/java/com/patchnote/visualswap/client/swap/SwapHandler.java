package com.patchnote.visualswap.client.swap;

import com.patchnote.visualswap.client.hud.HUDHandler;
import com.patchnote.visualswap.client.hud.SwapHotbarHighlight;
import com.patchnote.visualswap.client.hud.SwapWindowState;
import com.patchnote.visualswap.client.hud.click.ItemFlash;
import com.patchnote.visualswap.client.particles.AttackParticleProps;
import com.patchnote.visualswap.client.particles.ParticlesHandler;
import com.patchnote.visualswap.client.tracker.ClickTickState;
import com.patchnote.visualswap.client.tracker.ClickTickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static com.patchnote.visualswap.client.utils.Constants.HOTBAR_SLOTS;
import static com.patchnote.visualswap.client.utils.Constants.NO_SLOT;

public class SwapHandler
{
    public static final SwapHandler INSTANCE = new SwapHandler();

    private SwapHandler() { }

    private final SwapWindowState swapWindowState = new SwapWindowState();

    // Derived state carried across ticks (not part of the per-tick sensor snapshot).
    private int swapFromSlot = NO_SLOT;
    private int swapToSlot = NO_SLOT;
    /// Whether the item most recently switched to opts its rule into the swap-hit indicators — recomputed on every
    /// switch. Gates the pre-click "possible" glyph (which tracks the live switched-to item).
    private boolean effectsAllowed;
    /// The opt-in latched when the current attacked flash *began*, held for its whole duration (OR-extended as a chain
    /// grows). The glyph's attacked flash and the hotbar highlight read this, not {@link #effectsAllowed}, so switching
    /// away mid-flash keeps them on for the full window instead of cutting them short.
    private boolean flashEffectsAllowed;
    /// Ordered hotbar slots touched by the active chain (origin first, latest hit last).
    private final int[] chainTrail = new int[HOTBAR_SLOTS];
    private int chainTrailLen;
    private boolean attackedLastTick;
    private int lastChainCount;
    /// Whether the input observed this tick belongs to the two-tick attribute-swap window, including its end-of-tick
    /// observation bridge.
    private boolean attributeSwapThisTick;

    /* EVENTS */

    public void eventTick()
    {
        detectSwap();

        int tick = ClickTickTracker.getCurrentState().tick();
        boolean attacked = ClickTickTracker.INSTANCE.attacked();
        this.attributeSwapThisTick = attacked && this.swapWindowState.acceptsClick(tick);
        if (attacked)
        {
            this.swapWindowState.eventClick(tick);
        }

        updateAttackState();

        this.swapWindowState.eventTickEnd(tick);
    }

    public void eventReset()
    {
        this.swapWindowState.clear();
        this.swapFromSlot = NO_SLOT;
        this.swapToSlot = NO_SLOT;
        this.effectsAllowed = false;
        this.flashEffectsAllowed = false;
        this.chainTrailLen = 0;
        this.attackedLastTick = false;
        this.lastChainCount = 0;
        this.attributeSwapThisTick = false;

        HUDHandler.GLYPH.eventReset();
        SwapHotbarHighlight.INSTANCE.eventReset();
        ItemFlash.INSTANCE.reset();
    }

    public InteractionResult eventInteractEntity(Player player, Entity entity)
    {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player)
        {
            // fires before END_CLIENT_TICK, check again
            boolean itemSwitched = !ItemStack.isSameItem(
                    ClickTickTracker.getPreviousState().mainHand(),
                    player.getMainHandItem()
            );
            if (itemSwitched || this.swapWindowState.possible(player.tickCount) // check if possible
            )
            {
                // the hit isn't added to the chain until END_CLIENT_TICK, + 1 to include it
                int chainHits = this.swapWindowState.chainCount(player.tickCount) + 1;
                AttackParticleProps props = AttackParticleProps.detectAttackType(player, entity);
                ParticlesHandler.spawnParticles(client, entity, chainHits, props);
            }
        }
        return InteractionResult.PASS;
    }

    /* HELPERS */

    private void detectSwap()
    {
        ClickTickState current = ClickTickTracker.getCurrentState();
        ClickTickState previous = ClickTickTracker.getPreviousState();

        // Skip the first post-spawn tick so the empty -> held transition isn't read as a swap.
        if (!previous.initialized()) return;

        // A swap is a deliberate slot switch: either the held item changed, or the player selected a hotbar slot this
        // tick (hotbar key / scroll). The slot-select signal catches switches the item comparison can't — re-selecting
        // the slot already held, or switching to a different slot holding an identical item.
        boolean itemChanged = !ItemStack.isSameItem(previous.mainHand(), current.mainHand());

        if (current.mainHand().isEmpty())
        {
            this.swapWindowState.clear();
        }
        else if (itemChanged || current.slotSelected())
        {
            boolean piercingFail = current.hasPiercingComponent() && previous.cooldownAtTick() < 1.0f;
            this.swapWindowState.eventSwap(current.tick(), piercingFail);
            this.swapFromSlot = previous.selectedSlot();
            this.swapToSlot = current.selectedSlot();
            // Latch whether the switched-to item's rule opts into the swap-hit indicators (glyph + hotbar highlight) —
            // held for this swap window's lifetime.
            this.effectsAllowed = ItemFlash.showsEffectsFor(current.mainHand());
        }
    }

    /// Whether this tick's input belongs to the two-tick attribute-swap window. Read after {@link #eventTick()} by the
    /// item flash.
    public boolean attributeSwapThisTick() { return this.attributeSwapThisTick; }

    private void updateAttackState()
    {
        int tick = ClickTickTracker.getCurrentState().tick();
        boolean attacked = this.swapWindowState.attacked(tick);
        int chainCount = this.swapWindowState.chainCount(tick);

        // Seed the trail and latch the per-flash opt-in first, so the glyph below can read the latched value.
        highlightSlot(attacked, chainCount);

        // While a swap-hit flash is on screen the display belongs to the swap that started it, so gate it on the value
        // latched then (switching away mid-flash must not clobber it); before any click, use the live switched-to opt-in.
        boolean displayAllowed = attacked ? this.flashEffectsAllowed : this.effectsAllowed;
        HUDHandler.GLYPH.eventUpdate(
                displayAllowed && this.swapWindowState.visible(tick),
                attacked,
                this.swapWindowState.failed(tick),
                this.swapWindowState.consecutive(tick),
                chainCount
        );

        this.attackedLastTick = attacked;
        this.lastChainCount = chainCount;
    }


    private void highlightSlot(boolean attacked, int chainCount)
    {
        if (attacked && !this.attackedLastTick)
        {
            // Fresh chain: latch this swap's opt-in for the whole flash, and seed the trail with its origin+destination.
            this.flashEffectsAllowed = this.effectsAllowed;
            this.chainTrailLen = 0;
            addTrailSlot(this.swapFromSlot);
            addTrailSlot(this.swapToSlot);
        }
        else if (attacked && chainCount > this.lastChainCount)
        {
            // Chain extended this tick: append the latest swap's destination; opt the flash in if this hit did.
            this.flashEffectsAllowed |= this.effectsAllowed;
            addTrailSlot(this.swapToSlot);
        }
        // Gated like the glyph, but off the value latched when the flash began — so switching away mid-flash keeps the
        // highlight on for the flash's full duration instead of hiding it.
        SwapHotbarHighlight.INSTANCE.eventUpdate(
                attacked && this.flashEffectsAllowed, this.chainTrail, this.chainTrailLen, chainCount);
    }

    private void addTrailSlot(int slot)
    {
        if (slot < 0 || this.chainTrailLen >= this.chainTrail.length) return;
        // dedup consecutive
        if (this.chainTrailLen > 0 && this.chainTrail[this.chainTrailLen - 1] == slot) return;
        this.chainTrail[this.chainTrailLen++] = slot;
    }
}
