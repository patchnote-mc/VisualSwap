package com.patchnote.visualswap.client;

import com.patchnote.visualswap.SwapWindow;
import com.patchnote.visualswap.VisualSwap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;


public class VisualSwapClient implements ClientModInitializer
{
    private static final int NO_SLOT = -1;

    private final SwapWindow swapWindow = new SwapWindow();
    private final SwapHitGlyph glyph = new SwapHitGlyph();

    private ItemStack previousMainHand = ItemStack.EMPTY;
    private boolean primed;
    private boolean previousAttackDown;
    private boolean previousUseDown;
    private boolean previousSwinging;
    private int previousSwingTime;

    private int previousSelectedSlot;
    private int swapFromSlot = NO_SLOT;
    private int swapToSlot = NO_SLOT;
    private int highlightFromSlot = NO_SLOT;
    private int highlightToSlot = NO_SLOT;
    private boolean previousAttacked;

    @Override
    public void onInitializeClient()
    {
        VisualSwap.LOGGER.info("Visual Swap initializing ...");

        VisualSwapParticles.registerTypes();
        VisualSwapParticles.registerFactories();
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, "swap_hit_glyph"),
                this.glyph
        );

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        AttackEntityCallback.EVENT.register(this::onInteractEntity);
        UseEntityCallback.EVENT.register(this::onInteractEntity);
    }

    /* CALLBACKS */

    private void onEndClientTick(Minecraft client)
    {
        LocalPlayer player = client.player;
        if (player == null)
        {
            reset();
            this.glyph.updateState(false, false);
            SwapHotbarHighlight.INSTANCE.clear();
            return;
        }

        int tick = player.tickCount;
        int selectedSlot = player.getInventory().getSelectedSlot();

        ItemStack held = player.getMainHandItem();
        if (!this.primed) this.primed = true;
        else if (!ItemStack.isSameItem(this.previousMainHand, held))
        {
            this.swapWindow.onSwap(tick);
            this.swapFromSlot = this.previousSelectedSlot;
            this.swapToSlot = selectedSlot;
        }
        this.previousMainHand = held.copy();
        this.previousSelectedSlot = selectedSlot;

        // A fresh attack/use fires the attacked variant (onClick gates it to the swap window).
        // The spear jab takes a special path: startAttack() -> piercingAttack() (so AttackEntityCallback never
        // fires) and it's a fast sub-tick click the key-down edge misses. Detect the swing it always triggers
        // (player.swing(), Minecraft.java:1648) instead — the animation persists across the tick boundary.
        boolean attackDown = client.options.keyAttack.isDown();
        boolean useDown = client.options.keyUse.isDown();
        boolean swingStarted = player.swinging && (!this.previousSwinging || player.swingTime < this.previousSwingTime);
        boolean clicked =
                swingStarted || (attackDown && !this.previousAttackDown) || (useDown && !this.previousUseDown);
        this.previousAttackDown = attackDown;
        this.previousUseDown = useDown;
        this.previousSwinging = player.swinging;
        this.previousSwingTime = player.swingTime;
        if (clicked) this.swapWindow.onClick(tick);

        boolean attacked = this.swapWindow.attacked(tick);
        this.glyph.updateState(this.swapWindow.visible(tick), attacked);

        // The glyph turning attacked (from possible) freezes that swap's two hotbar slots for the flash's duration.
        if (attacked && !this.previousAttacked)
        {
            this.highlightFromSlot = this.swapFromSlot;
            this.highlightToSlot = this.swapToSlot;
        }
        this.previousAttacked = attacked;
        SwapHotbarHighlight.INSTANCE.update(attacked, this.highlightFromSlot, this.highlightToSlot);

        this.swapWindow.endTick(tick);
    }

    /** Attack or use on an entity during a swap window -> spawn the burst on the target. */
    private InteractionResult onInteractEntity(Player player, Level level, InteractionHand hand, Entity entity,
                                               HitResult hitResult)
    {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player)
        {
            // Fires during handleKeybinds, before END_CLIENT_TICK sees this swap — so check it live here:
            // a same-tick item change, or an already-open window from a previous tick.
            boolean switchedThisTick = !ItemStack.isSameItem(this.previousMainHand, player.getMainHandItem());
            if (switchedThisTick || this.swapWindow.possible(player.tickCount))
            {
                spawnParticles(client, entity);
            }
        }
        return InteractionResult.PASS;
    }

    /* HELPERS */

    private void reset()
    {
        this.previousMainHand = ItemStack.EMPTY;
        this.primed = false;
        this.previousAttackDown = false;
        this.previousUseDown = false;
        this.previousSwinging = false;
        this.previousSwingTime = 0;
        this.previousSelectedSlot = 0;
        this.swapFromSlot = NO_SLOT;
        this.swapToSlot = NO_SLOT;
        this.highlightFromSlot = NO_SLOT;
        this.highlightToSlot = NO_SLOT;
        this.previousAttacked = false;
        this.swapWindow.clear();
    }

    private static void spawnParticles(Minecraft client, Entity target)
    {
        if (client.level == null)
        {
            return;
        }
        // A gaussian cloud around the target's mid-height (the attacked variant).
        SimpleParticleType particle = VisualSwapParticles.SWAP_ATTACKED;
        int count = 18;
        double spread = 0.45;
        RandomSource random = target.getRandom();
        double cx = target.getX();
        double cy = target.getY(0.5);
        double cz = target.getZ();
        for (int i = 0; i < count; i++)
        {
            double ox = random.nextGaussian() * spread;
            double oy = random.nextGaussian() * spread;
            double oz = random.nextGaussian() * spread;
            client.level.addParticle(particle, cx + ox, cy + oy, cz + oz, 0.0, 0.0, 0.0);
        }
    }
}
