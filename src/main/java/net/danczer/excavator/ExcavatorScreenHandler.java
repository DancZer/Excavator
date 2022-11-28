package net.danczer.excavator;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class ExcavatorScreenHandler extends ScreenHandler {
    private static final int SlotSize = 18;
    private static final int InventoryRowCount = 4;
    private static final int InventoryColumnCount = 9;
    public static final int InventorySize = InventoryRowCount * InventoryColumnCount;
    private final Inventory inventory;

    public ExcavatorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(InventorySize));
    }

    public ExcavatorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ExcavatorMod.EXCAVATOR_SCREEN_HANDLER, syncId);
        checkSize(inventory, InventorySize);
        this.inventory = inventory;

        inventory.onOpen(playerInventory.player);

        for (int i = 0; i < InventorySize; ++i) {
            int x = i %InventoryColumnCount;
            int y = Math.floorDiv(i , InventoryColumnCount);

            this.addSlot(new Slot(inventory, i, 8 + x * SlotSize, 20 + y * SlotSize));
        }

        for (int i = 0; i < InventoryColumnCount; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * SlotSize, 109));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) { return this.inventory.canPlayerUse(player); }

    // Shift + Player Inv Slot
    @Override
    public ItemStack transferSlot(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (invSlot < this.inventory.size()) {
                if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }

        return newStack;
    }
}