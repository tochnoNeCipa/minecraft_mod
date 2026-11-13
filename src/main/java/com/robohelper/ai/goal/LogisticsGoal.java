package com.robohelper.ai.goal;

import com.robohelper.ai.ContainerFinder;
import com.robohelper.ai.WorkNavigation;
import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.inventory.InventoryTransfer;
import com.robohelper.inventory.LogisticsCargo;
import com.robohelper.module.LogisticsModule;
import com.robohelper.screen.RobotStatus;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Приоритетная поездка к доступным контейнерам до выгрузки всего разрешённого груза. */
public final class LogisticsGoal extends Goal {
    private final RobotEntity robot;
    private final LogisticsModule module;
    private final Map<BlockPos, Long> inaccessible = new HashMap<>();
    private BlockPos destination;
    private BlockPos center;
    private Path path;
    private BlockPos stand;
    private long approachStarted;
    private boolean trip;
    private boolean noDestination;

    public LogisticsGoal(RobotEntity robot, LogisticsModule module) {
        this.robot = robot;
        this.module = module;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!robot.isOnGround()) {
            return false;
        }
        if (center != null && !center.equals(robot.getZoneCenter())) {
            forgetDestination();
            trip = false;
            center = null;
            noDestination = false;
        }
        List<LogisticsCargo.Cargo> cargo = LogisticsCargo.exports(robot.getInventory());
        if (cargo.isEmpty()) {
            if (trip || noDestination) {
                robot.clearModuleStatus();
            }
            trip = false;
            noDestination = false;
            center = null;
            forgetDestination();
            return false;
        }
        if (!trip && !LogisticsCargo.needsUnload(robot.getInventory()) && !robot.isStorageBlocked()) {
            return false;
        }
        if (destination != null) {
            return true;
        }
        status(noDestination ? RobotStatus.NO_CONTAINER : RobotStatus.SEARCHING_CONTAINER);
        if (!robot.tryBeginWorkScan(module.getId())) {
            return false;
        }
        long now = robot.getWorld().getTime();
        inaccessible.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (BlockPos pos : ContainerFinder.find(robot)) {
            if (inaccessible.containsKey(pos)) {
                continue;
            }
            Inventory inventory = ContainerFinder.resolve(robot, pos);
            if (inventory == null || !acceptsCargo(inventory, cargo)) {
                continue;
            }
            path = near(pos) ? null : WorkNavigation.findPathNear(robot, pos);
            if (!near(pos) && path == null) {
                inaccessible.put(pos, now + RobotConfig.WORK_PATH_TIMEOUT);
                continue;
            }
            destination = pos;
            center = robot.getZoneCenter();
            trip = true;
            noDestination = false;
            return true;
        }
        noDestination = true;
        status(RobotStatus.NO_CONTAINER);
        robot.notifyWorkOwner("message.robohelper.no_container");
        return false;
    }

    @Override
    public void start() {
        approachStarted = robot.getWorld().getTime();
        if (destination != null && !near(destination)) {
            if (path == null) {
                path = WorkNavigation.findPathNear(robot, destination);
            }
            if (path == null || !robot.getNavigation().startMovingAlong(path, RobotConfig.NAVIGATION_SPEED)) {
                reject();
            } else {
                stand = path.getEnd().getBlockPos();
                path = null;
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return destination != null && sameZone() && !LogisticsCargo.exports(robot.getInventory()).isEmpty()
                && (near(destination) || robot.getWorld().getTime() - approachStarted < RobotConfig.WORK_PATH_TIMEOUT);
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        robot.getNavigation().stop();
        path = null;
        if (destination != null && (!sameZone() || !near(destination)
                && robot.getWorld().getTime() - approachStarted >= RobotConfig.WORK_PATH_TIMEOUT)) {
            reject();
        }
        if (LogisticsCargo.exports(robot.getInventory()).isEmpty()) {
            trip = false;
            cancelDestination();
            robot.clearModuleStatus();
        }
    }

    @Override
    public void tick() {
        if (destination == null || !sameZone()) {
            return;
        }
        Inventory inventory = ContainerFinder.resolve(robot, destination);
        List<LogisticsCargo.Cargo> cargo = LogisticsCargo.exports(robot.getInventory());
        if (cargo.isEmpty()) {
            trip = false;
            cancelDestination();
            robot.clearModuleStatus();
            return;
        }
        if (inventory == null || !acceptsCargo(inventory, cargo)) {
            cancelDestination();
            return;
        }
        status(RobotStatus.UNLOADING);
        if (!near(destination)) {
            if (robot.getNavigation().isIdle()) {
                if (stand != null && distance(stand) <= 1.0) {
                    robot.getMoveControl().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                            RobotConfig.NAVIGATION_SPEED);
                } else if (stand == null) {
                    start();
                } else {
                    reject();
                }
            }
            return;
        }
        robot.getNavigation().stop();
        module.performAction(robot, RobotConfig.TRANSFER_ENERGY_COST, this::transferOne);
    }

    private boolean transferOne() {
        Inventory target = ContainerFinder.resolve(robot, destination);
        if (target == null || !sameZone()) {
            return false;
        }
        for (LogisticsCargo.Cargo cargo : LogisticsCargo.exports(robot.getInventory())) {
            if (!LogisticsCargo.canAccept(target, cargo.stack())) {
                continue;
            }
            ItemStack source = robot.getInventory().getStack(cargo.slot());
            ItemStack moving = source.split(cargo.stack().getCount());
            ItemStack remaining = InventoryTransfer.insert(target, moving, 0, target.size());
            int moved = moving.getCount() - remaining.getCount();
            source.increment(remaining.getCount());
            robot.getInventory().setStack(cargo.slot(), source.isEmpty() ? ItemStack.EMPTY : source);
            return moved > 0;
        }
        return false;
    }

    private static boolean acceptsCargo(Inventory inventory, List<LogisticsCargo.Cargo> cargo) {
        return cargo.stream().anyMatch(stack -> LogisticsCargo.canAccept(inventory, stack.stack()));
    }

    private void status(RobotStatus status) {
        robot.setModuleStatus(status, module.getPriority());
    }

    private double distance(BlockPos pos) {
        return robot.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private boolean near(BlockPos pos) {
        return distance(pos) <= RobotConfig.WORK_REACH * RobotConfig.WORK_REACH;
    }

    private boolean sameZone() {
        return center != null && center.equals(robot.getZoneCenter()) && robot.isInsideWorkZone(destination);
    }

    private void reject() {
        if (destination != null) {
            inaccessible.put(destination, robot.getWorld().getTime() + RobotConfig.WORK_PATH_TIMEOUT);
        }
        cancelDestination();
    }

    private void forgetDestination() {
        destination = null;
        path = null;
        stand = null;
    }

    private void cancelDestination() {
        forgetDestination();
        robot.getNavigation().stop();
    }
}
