package net.danczer.excavator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.ItemGroup;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;


public class ExcavatorMod implements ModInitializer {
    public static final String MOD_ID = "excavator";
    public static final Identifier EXCAVATOR_IDENTIFIER = new Identifier(MOD_ID, "excavator_minecart");

    public static final ExcavatorMinecartItem EXCAVATOR_MINECART_ITEM =
            Registry.register(
                    Registry.ITEM,
                    EXCAVATOR_IDENTIFIER,
                    new ExcavatorMinecartItem(new FabricItemSettings().group(ItemGroup.TRANSPORTATION)));
    public static final ScreenHandlerType<ExcavatorScreenHandler> EXCAVATOR_SCREEN_HANDLER =
            Registry.register(
                    Registry.SCREEN_HANDLER,
                    EXCAVATOR_IDENTIFIER,
                    new ScreenHandlerType<>(ExcavatorScreenHandler::new));

    public static EntityType<ExcavatorMinecartEntity> EXCAVATOR_ENTITY = Registry.register(
                    Registry.ENTITY_TYPE,
                    EXCAVATOR_IDENTIFIER,
                    FabricEntityTypeBuilder.create(SpawnGroup.MISC, ExcavatorMinecartEntity::new)
                        .forceTrackedVelocityUpdates(true)
                        .dimensions(EntityDimensions.fixed(0.98f, 0.7f))
                        .trackRangeBlocks(8)
                        .build()
    );

    public static final EntityType<CubeEntity> CUBE = Registry.register(
            Registry.ENTITY_TYPE,
            new Identifier("entitytesting", "cube"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CubeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.75f, 0.75f))
                    .build()
    );

    @Override
    public void onInitialize() {
    }
}
