package com.robohelper.entity;

import com.robohelper.config.RobotConfig;
import com.robohelper.item.RobotModuleItem;
import com.robohelper.inventory.InventoryTransfer;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.Identifier;

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
        return robot.isAlive() && player.getWorld() == robot.getWorld() && robot.isOwner(player)
                && player.squaredDistanceTo(robot) <= RobotConfig.INTERACTION_DISTANCE_SQUARED;
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return canInsert(this, slot, stack);
    }

    /** Общие фильтры для настоящих слотов и их синхронизированной клиентской копии. */
    public static boolean canInsert(Inventory inventory, int slot, ItemStack stack) {
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
        if (!(stack.getItem() instanceof RobotModuleItem module)) {
            return false;
        }
        for (int other = FIRST_MODULE_SLOT; other < SIZE; other++) {
            ItemStack existing = inventory.getStack(other);
            if (other != slot && existing.getItem() instanceof RobotModuleItem installed
                    && installed.getModuleId().equals(module.getModuleId())) {
                return false;
            }
        }
        return true;
    }

    /** Проверяет установленную профессию без зависимости от класса её поведения. */
    public boolean hasModule(Identifier moduleId) {
        for (int slot = FIRST_MODULE_SLOT; slot < SIZE; slot++) {
            if (stacks.get(slot).getItem() instanceof RobotModuleItem module
                    && module.getModuleId().equals(moduleId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        stacks.clear();
        markDirty();
    }

    /** Помещает предметы только в 18 слотов хранения, возвращая непоместившуюся копию. */
    public ItemStack insertStorage(ItemStack stack) {
        return InventoryTransfer.insert(this, stack, 0, RobotConfig.STORAGE_SLOTS);
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
