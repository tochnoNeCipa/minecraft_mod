package com.robohelper.entity;

import com.robohelper.config.RobotConfig;
import com.robohelper.item.RobotModuleItem;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;

/** Инвентарь робота: хранение, топливо и три слота предметов-модулей. */
public final class RobotInventory implements Inventory {
    public static final int FUEL_SLOT = RobotConfig.STORAGE_SLOTS;
    public static final int FIRST_MODULE_SLOT = FUEL_SLOT + 1;
    public static final int SIZE = FIRST_MODULE_SLOT + RobotConfig.MODULE_SLOTS;

    private final RobotEntity robot;
    private final DefaultedList<ItemStack> stacks = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    public RobotInventory(RobotEntity robot) {
        this.robot = robot;
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return stacks.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getStack(int slot) {
        return stacks.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(stacks, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(stacks, slot);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        stacks.set(slot, stack);
        markDirty();
    }

    @Override
    public void markDirty() {
        // Сущности сохраняются вместе с чанком; подключение модулей добавится отдельно.
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return robot.isAlive() && robot.isOwner(player)
                && player.squaredDistanceTo(robot) <= RobotConfig.INTERACTION_DISTANCE_SQUARED;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return false;
        }
        if (stack.isEmpty() || slot < RobotConfig.STORAGE_SLOTS) {
            return true;
        }
        if (slot == FUEL_SLOT) {
            Integer burnTime = FuelRegistry.INSTANCE.get(stack.getItem());
            return burnTime != null && burnTime > 0;
        }
        return stack.getItem() instanceof RobotModuleItem;
    }

    @Override
    public void clear() {
        stacks.clear();
        markDirty();
    }

    /** Записывает слоты с сохранением индексов, количества и NBT предметов. */
    public void writeNbt(NbtCompound nbt) {
        Inventories.writeNbt(nbt, stacks);
    }

    /** Полностью заменяет содержимое при загрузке, включая пустые слоты. */
    public void readNbt(NbtCompound nbt) {
        stacks.clear();
        Inventories.readNbt(nbt, stacks);
        markDirty();
    }
}
