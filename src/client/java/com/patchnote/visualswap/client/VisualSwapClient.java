package com.patchnote.visualswap.client;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.hud.HUDHandler;
import com.patchnote.visualswap.client.hud.click.ItemFlashHandler;
import com.patchnote.visualswap.client.swap.SwapHandler;
import com.patchnote.visualswap.client.tracker.ClickTickTracker;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;


public class VisualSwapClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        VisualSwap.LOGGER.info("Visual Swap initializing ...");

        // registration
        AutoConfig.register(ModConfig.class, Toml4jConfigSerializer::new);
        HUDHandler.register();

        // event callbacks
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        AttackEntityCallback.EVENT.register(this::onInteractEntity);
        UseEntityCallback.EVENT.register(this::onInteractEntity);
    }

    /* CALLBACKS */

    private void onEndClientTick(Minecraft client)
    {
        if (client.player == null || !ModConfig.get().modEnabled)
        {
            // invoked when not in a world, or the mod is switched off — clear any carried state so nothing renders
            reset();
            return;
        }

        ClickTickTracker.INSTANCE.eventClientTickStart(client);

        SwapHandler.INSTANCE.eventTick();
        ItemFlashHandler.eventEndClientTick();

        ClickTickTracker.INSTANCE.eventClientTickEnd();
    }

    private InteractionResult onInteractEntity(Player player, Level level, InteractionHand hand, Entity entity,
                                               HitResult hitResult)
    {
        if (!ModConfig.get().modEnabled) return InteractionResult.PASS;
        return SwapHandler.INSTANCE.eventInteractEntity(player, entity);
    }

    /* HELPERS */

    private void reset()
    {
        ClickTickTracker.INSTANCE.eventReset();
        SwapHandler.INSTANCE.eventReset();
    }
}
