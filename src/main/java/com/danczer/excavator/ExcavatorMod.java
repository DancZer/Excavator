package com.danczer.excavator;

import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.inventory.container.ContainerType;
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
    public static EntityType<ExcavatorMinecartEntity> EXCAVATOR_ENTITY;
    public static ContainerType<ExcavatorContainer> EXCAVATOR_CONTAINER;
    public static ExcavatorMinecartItem EXCAVATOR_ITEM;

    public ExcavatorMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ExcavatorMod::setupClient);
    }

    @SubscribeEvent
    public static void setupClient(final FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(EXCAVATOR_ENTITY, MinecartRenderer<ExcavatorMinecartEntity>::new);

        ScreenManager.registerFactory(EXCAVATOR_CONTAINER, ExcavatorScreen::new);
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class RegistryEvents {

        @SubscribeEvent
        public static void onContainersRegistry(final RegistryEvent.Register<ContainerType<?>> event) {
            EXCAVATOR_CONTAINER = new ContainerType<>(ExcavatorContainer::new);
            EXCAVATOR_CONTAINER.setRegistryName("excavator");
            event.getRegistry().register(EXCAVATOR_CONTAINER);
        }

        @SubscribeEvent
        public static void onBlocksRegistry(final RegistryEvent.Register<Item> event) {
        }

        @SubscribeEvent
        public static void onItemsRegistry(final RegistryEvent.Register<Item> event) {
            Item.Properties itemProp = new Item.Properties();
            itemProp.group(ItemGroup.TRANSPORTATION);
            itemProp.maxStackSize(1);

            EXCAVATOR_ITEM = new ExcavatorMinecartItem(itemProp);
            EXCAVATOR_ITEM.setRegistryName("excavator_minecart");

            event.getRegistry().register(EXCAVATOR_ITEM);
        }

        @SubscribeEvent
        public static void onEntitiesRegistry(final RegistryEvent.Register<EntityType<?>> event) {
            EXCAVATOR_ENTITY = EntityType.Builder.
                    <ExcavatorMinecartEntity>create(ExcavatorMinecartEntity::new, EntityClassification.MISC)
                    .setCustomClientFactory(ExcavatorMinecartEntity::new)
                    .setShouldReceiveVelocityUpdates(true)
                    .size(0.98F, 0.7F)
                    .trackingRange(8)
                    .build("excavator_minecart");

            EXCAVATOR_ENTITY.setRegistryName("excavator_minecart");

            event.getRegistry().register(EXCAVATOR_ENTITY);
        }
    }
}
