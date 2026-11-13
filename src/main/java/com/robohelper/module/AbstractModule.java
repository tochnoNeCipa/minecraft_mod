package com.robohelper.module;

import com.robohelper.entity.RobotEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import java.util.function.BooleanSupplier;

/** Шаблон профессии: уровни, интервал и учёт только успешных действий. */
public abstract class AbstractModule implements RobotModule {
    private final Identifier id;
    private long nextActionTick;

    protected AbstractModule(Identifier id) {
        this.id = id;
    }

    @Override
    public final Identifier getId() {
        return id;
    }

    @Override
    public void onInstall(RobotEntity robot) {
    }

    @Override
    public void onRemove(RobotEntity robot) {
    }

    @Override
    public int getLevel(ItemStack stack) {
        return ModuleProgress.level(stack);
    }

    @Override
    public int getActionInterval(ItemStack stack) {
        return ModuleProgress.interval(stack);
    }

    @Override
    public int getEnergyCost(ItemStack stack, int base) {
        return ModuleProgress.energyCost(stack, base);
    }

    /** Выполняет серверное действие с резервированием энергии и возвратом при отказе. */
    public final boolean performAction(RobotEntity robot, int baseCost, BooleanSupplier action) {
        ItemStack stack = robot.getModules().getStack(this);
        long now = robot.getWorld().getTime();
        if (robot.getWorld().isClient || stack.isEmpty() || !robot.canStartModuleWork() || now < nextActionTick) {
            return false;
        }
        int cost = getEnergyCost(stack, baseCost);
        if (!robot.tryConsumeEnergy(cost)) {
            return false;
        }
        boolean completed = false;
        try {
            completed = action.getAsBoolean();
            if (!completed) {
                return false;
            }
            ModuleProgress.recordAction(stack);
            robot.getInventory().markDirty();
            nextActionTick = now + getActionInterval(stack);
            return true;
        } finally {
            if (!completed) {
                robot.setEnergy(robot.getEnergy() + cost);
            }
        }
    }
}
