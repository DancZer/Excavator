package com.danczer.excavator;

import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.item.minecart.AbstractMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("excavator")
public class ExcavatorMod {
    public static EntityType<ExcavatorMinecartEntity> EXCAVATOR;

    public ExcavatorMod()
    {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ExcavatorMod::setupClient);
    }

    @SubscribeEvent
    public static void setupClient(final FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(EXCAVATOR, MinecartRenderer<ExcavatorMinecartEntity>::new);
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {

        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Item> event) {
        }

        @SubscribeEvent
        public static void onItemsRegistry(final RegistryEvent.Register<Item> event) {
            Item.Properties itemProp = new Item.Properties();
            itemProp.group(ItemGroup.TRANSPORTATION);
            itemProp.maxStackSize(1);

            ExcavatorMinecartItem excavatorMinecartItem = new ExcavatorMinecartItem(itemProp);
            excavatorMinecartItem.setRegistryName("excavator_minecart");

            event.getRegistry().register(excavatorMinecartItem);
        }

        @SubscribeEvent
        public static void onEntitiesRegistry(final RegistryEvent.Register<EntityType<?>> event) {
            EXCAVATOR = EntityType.Builder.
                    <ExcavatorMinecartEntity>create(ExcavatorMinecartEntity::new, EntityClassification.MISC)
                    .setCustomClientFactory(ExcavatorMinecartEntity::new)
                    .setShouldReceiveVelocityUpdates(true)
                    .size(0.98F, 0.7F)
                    .trackingRange(8)
                    .build("excavator_minecart");

            EXCAVATOR.setRegistryName("excavator_minecart");

            event.getRegistry().register(EXCAVATOR);
        }
    }
}
