package com.robohelper.module;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.item.RobotModuleItem;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Подключает и снимает цели по содержимому слотов, сохраняя живой ItemStack прогресса. */
public final class ModuleManager {
    private final RobotEntity robot;
    private final Map<Integer, Installed> active = new HashMap<>();

    public ModuleManager(RobotEntity robot) {
        this.robot = robot;
    }

    /** Сверяет три слота перед ИИ; прямое изменение ItemStack также не обходит снятие. */
    public void synchronize() {
        if (robot.getWorld().isClient) {
            return;
        }
        for (int slot : new ArrayList<>(active.keySet())) {
            Installed installed = active.get(slot);
            ItemStack current = robot.getInventory().getStack(slot);
            if (current != installed.stack || current.isEmpty()) {
                remove(slot);
            }
        }
        Set<net.minecraft.util.Identifier> seen = new HashSet<>();
        for (int slot = RobotInventory.FIRST_MODULE_SLOT; slot < RobotInventory.SIZE; slot++) {
            ItemStack stack = robot.getInventory().getStack(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof RobotModuleItem item)
                    || !seen.add(item.getModuleId())) {
                remove(slot);
                continue;
            }
            if (active.containsKey(slot)) {
                continue;
            }
            RobotModule module = ModuleRegistry.create(item.getModuleId());
            if (module == null) {
                continue;
            }
            List<Goal> goals = new ArrayList<>();
            for (Goal goal : module.createGoals(robot)) {
                goals.add(new ModuleGoal(slot, module, stack, goal));
            }
            active.put(slot, new Installed(module, stack, goals));
            int priority = module.getPriority() * RobotConfig.MODULE_PRIORITY_SCALE
                    + slot - RobotInventory.FIRST_MODULE_SLOT;
            goals.forEach(goal -> robot.addModuleGoal(priority, goal));
            module.onInstall(robot);
        }
    }

    private void remove(int slot) {
        Installed installed = active.remove(slot);
        if (installed != null) {
            installed.goals.forEach(robot::removeModuleGoal);
            installed.module.onRemove(robot);
        }
    }

    public void clear() {
        for (int slot : new ArrayList<>(active.keySet())) {
            remove(slot);
        }
    }

    /** Устаревший экземпляр после снятия не получает доступ к новому предмету того же типа. */
    public ItemStack getStack(RobotModule module) {
        for (Map.Entry<Integer, Installed> entry : active.entrySet()) {
            Installed installed = entry.getValue();
            if (installed.module == module && installed.stack == robot.getInventory().getStack(entry.getKey())
                    && !installed.stack.isEmpty()) {
                return installed.stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    public RobotModule getModule(net.minecraft.util.Identifier id) {
        return active.values().stream().map(Installed::module)
                .filter(module -> module.getId().equals(id)).findFirst().orElse(null);
    }

    private record Installed(RobotModule module, ItemStack stack, List<Goal> goals) {
    }

    private final class ModuleGoal extends Goal {
        private final int slot;
        private final RobotModule module;
        private final ItemStack stack;
        private final Goal delegate;

        private ModuleGoal(int slot, RobotModule module, ItemStack stack, Goal delegate) {
            this.slot = slot;
            this.module = module;
            this.stack = stack;
            this.delegate = delegate;
            setControls(java.util.EnumSet.copyOf(delegate.getControls()));
        }

        private boolean available() {
            return robot.canStartModuleWork() && !stack.isEmpty()
                    && robot.getInventory().getStack(slot) == stack && active.get(slot) != null
                    && active.get(slot).module == module;
        }

        @Override
        public boolean canStart() {
            return available() && delegate.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return available() && delegate.shouldContinue();
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void stop() {
            delegate.stop();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return delegate.shouldRunEveryTick();
        }

        @Override
        public void tick() {
            if (available()) {
                delegate.tick();
            }
        }
    }
}
