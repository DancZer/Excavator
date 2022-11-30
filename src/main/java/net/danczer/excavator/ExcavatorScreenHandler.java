package net.danczer.excavator;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ExcavatorScreenHandler extends ScreenHandler {
    public static final int InventorySize = 9;
    private final Inventory excavatorInventory;

    public ExcavatorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(InventorySize));
    }


    public ExcavatorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory excavatorInventory) {
        super(ExcavatorMod.EXCAVATOR_SCREEN_HANDLER, syncId);
        this.excavatorInventory = excavatorInventory;
        checkSize(excavatorInventory, InventorySize);
        excavatorInventory.onOpen(playerInventory.player);

        for (int colIdx = 0; colIdx < InventorySize; ++colIdx) {
            this.addSlot(new Slot(excavatorInventory, colIdx, 8 + colIdx * 18, 20));
        }

        for (int rowIdx = 0; rowIdx < 3; ++rowIdx) {
            for (int colIdx = 0; colIdx < 9; ++colIdx) {
                this.addSlot(new Slot(playerInventory, colIdx + rowIdx * 9 + 9, 8 + colIdx * 18, rowIdx * 18 + 51));
            }
        }

        for (int colIdx = 0; colIdx < 9; ++colIdx) {
            this.addSlot(new Slot(playerInventory, colIdx, 8 + colIdx * 18, 109));
        }
    }

    public boolean canUse(PlayerEntity player) { return this.excavatorInventory.canPlayerUse(player); }

    public ItemStack transferSlot(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = getSlot(index);
        if (slot != null && slot.hasStack()) {
            ItemStack itemStack2 = slot.getStack();
            itemStack = itemStack2.copy();
            if (index < this.excavatorInventory.size()) {
                if (!this.insertItem(itemStack2, this.excavatorInventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(itemStack2, 0, this.excavatorInventory.size(), false)) {
                return ItemStack.EMPTY;
            }

            if (itemStack2.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return itemStack;
    }

    public void close(PlayerEntity player) {
        super.close(player);
        this.excavatorInventory.onClose(player);
    }
}