package com.robohelper.module;

import com.robohelper.config.RobotConfig;
import net.minecraft.item.ItemStack;

/** Счётчик предмета и общие правила роста, интервалов и расхода энергии. */
public final class ModuleProgress {
    public static final String COUNTER_KEY = "RoboHelper.Counter";

    private ModuleProgress() {
    }

    public static int counter(ItemStack stack) {
        return stack.hasNbt() ? Math.max(0, stack.getNbt().getInt(COUNTER_KEY)) : 0;
    }

    public static int level(ItemStack stack) {
        int count = counter(stack);
        return count < RobotConfig.MODULE_LEVEL_TWO_ACTIONS ? 1
                : count < RobotConfig.MODULE_LEVEL_THREE_ACTIONS ? 2 : 3;
    }

    public static int interval(ItemStack stack) {
        return RobotConfig.MODULE_BASE_INTERVAL - (level(stack) - 1) * RobotConfig.MODULE_INTERVAL_STEP;
    }

    /** Округляет вверх, поэтому маленькая стоимость не становится нулевой. */
    public static int energyCost(ItemStack stack, int base) {
        if (base < 0) {
            throw new IllegalArgumentException("Negative action cost");
        }
        int percent = 100 - (level(stack) - 1) * RobotConfig.MODULE_DISCOUNT_STEP;
        return (int) (((long) base * percent + 99) / 100);
    }

    public static void recordAction(ItemStack stack) {
        int count = counter(stack);
        stack.getOrCreateNbt().putInt(COUNTER_KEY, count == Integer.MAX_VALUE ? count : count + 1);
    }
}
