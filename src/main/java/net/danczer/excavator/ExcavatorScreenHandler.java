package net.danczer.excavator;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class ExcavatorScreenHandler extends ScreenHandler {
    public static final int InventorySize = 9;
    private final Inventory inventory;

    public ExcavatorScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(InventorySize));
    }

    public ExcavatorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        super(ExcavatorMod.EXCAVATOR_SCREEN_HANDLER, syncId);
        checkSize(inventory, InventorySize);
        this.inventory = inventory;

        inventory.onOpen(playerInventory.player);

        for (int j = 0; j < InventorySize; ++j) {
            this.addSlot(new Slot(inventory, j, 8 + j * 18, 20));
        }

        for (int l = 0; l < 3; ++l) {
            for (int k = 0; k < 9; ++k) {
                this.addSlot(new Slot(playerInventory, k + l * 9 + 9, 8 + k * 18, l * 18 + 51));
            }
        }

        for (int i1 = 0; i1 < 9; ++i1) {
            this.addSlot(new Slot(playerInventory, i1, 8 + i1 * 18, 109));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) { return this.inventory.canPlayerUse(player); }

    // Shift + Player Inv Slot
    @Override
    public ItemStack transferSlot(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);
        if (slot != null && slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();
            if (invSlot < this.inventory.size()) {
                if (!this.insertItem(originalStack, this.inventory.size(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.insertItem(originalStack, 0, this.inventory.size(), false)) {
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