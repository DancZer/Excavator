package net.danczer.excavator.client;

import net.danczer.excavator.ExcavatorMinecartEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;

public class ExcavatorMinecartEntityRenderer extends MinecartEntityRenderer<ExcavatorMinecartEntity> {
    public ExcavatorMinecartEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, EntityModelLayers.CHEST_MINECART);
    }
}
