package net.danczer.excavator.client;

import net.danczer.excavator.ExcavatorMinecartEntity;
import net.danczer.excavator.ExcavatorMod;
import net.danczer.excavator.ExcavatorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

@Environment(EnvType.CLIENT)
public class ExcavatorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HandledScreens.register(ExcavatorMod.EXCAVATOR_SCREEN_HANDLER, ExcavatorScreen::new);
        EntityRendererRegistry.register(ExcavatorMod.EXCAVATOR_ENTITY, ExcavatorMinecartEntityRenderer::new);
    }
}
