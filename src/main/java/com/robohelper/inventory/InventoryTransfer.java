package com.robohelper.inventory;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

/** Перенос без удаления остатка, в том числе при полном инвентаре творческого игрока. */
public final class InventoryTransfer {
    private InventoryTransfer() {
    }

    /** Возвращает непоместившуюся копию; исходный стек не изменяет. */
    public static ItemStack insert(Inventory inventory, ItemStack stack, int first, int end) {
        ItemStack remaining = stack.copy();
        for (int slot = first; slot < end && !remaining.isEmpty(); slot++) {
            ItemStack existing = inventory.getStack(slot);
            if (!existing.isEmpty() && ItemStack.canCombine(existing, remaining)) {
                int room = Math.max(0, Math.min(existing.getMaxCount(), inventory.getMaxCountPerStack())
                        - existing.getCount());
                int amount = Math.min(room, remaining.getCount());
                existing.increment(amount);
                remaining.decrement(amount);
            }
        }
        for (int slot = first; slot < end && !remaining.isEmpty(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                int amount = Math.min(remaining.getCount(),
                        Math.min(remaining.getMaxCount(), inventory.getMaxCountPerStack()));
                inventory.setStack(slot, remaining.copyWithCount(amount));
                remaining.decrement(amount);
            }
        }
        inventory.markDirty();
        return remaining;
    }
}
