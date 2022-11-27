package net.danczer.excavator;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
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
    @Override
    public void onInitialize() {
    }
}
