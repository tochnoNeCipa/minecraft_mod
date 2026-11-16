package com.robohelper.ai.goal;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.module.GuardModule;
import com.robohelper.screen.RobotStatus;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Hand;
import java.util.Comparator;

/** Ближняя охрана: выбор враждебной цели, ванильная погоня и оплачиваемые удары. */
public final class GuardGoal extends MeleeAttackGoal {
    private final RobotEntity robot;
    private final GuardModule module;
    private long lastHitTick;
    private boolean routeBlocked;

    public GuardGoal(RobotEntity robot, GuardModule module) {
        super(robot, RobotConfig.NAVIGATION_SPEED, true);
        this.robot = robot;
        this.module = module;
    }

    private boolean eligible(LivingEntity target) {
        return target instanceof HostileEntity && target.isAlive() && !target.isRemoved()
                && !RobotConfig.GUARD_NEUTRAL_TYPES.contains(target.getType())
                && robot.squaredDistanceTo(target) <= RobotConfig.GUARD_RANGE * RobotConfig.GUARD_RANGE
                && robot.isInsideWorkZone(target.getBlockPos());
    }

    @Override
    public boolean canStart() {
        if (!robot.isOnGround() || !robot.tryBeginWorkScan(module.getId())) {
            return false;
        }
        var candidates = robot.getWorld().getEntitiesByClass(HostileEntity.class,
                robot.getBoundingBox().expand(RobotConfig.GUARD_RANGE), this::eligible);
        candidates.sort(Comparator.comparingDouble(robot::squaredDistanceTo));
        for (HostileEntity candidate : candidates) {
            if (!robot.getVisibilityCache().canSee(candidate)) {
                continue;
            }
            Path path = robot.getNavigation().findPathTo(candidate, 0);
            if (!insideZone(path) || !path.reachesTarget()) {
                continue;
            }
            robot.setTarget(candidate);
            if (super.canStart()) {
                return true;
            }
            robot.setTarget(null);
            return false;
        }
        return false;
    }

    @Override
    public void start() {
        lastHitTick = robot.getWorld().getTime();
        routeBlocked = false;
        super.start();
    }

    @Override
    public boolean shouldContinue() {
        return !routeBlocked && eligible(robot.getTarget())
                && robot.getWorld().getTime() - lastHitTick < RobotConfig.WORK_PATH_TIMEOUT
                && super.shouldContinue();
    }

    @Override
    public void tick() {
        if (routeBlocked || !eligible(robot.getTarget())) {
            robot.getNavigation().stop();
            return;
        }
        robot.setModuleStatus(RobotStatus.GUARDING, module.getPriority());
        super.tick();
        Path path = robot.getNavigation().getCurrentPath();
        if (path != null && !insideZone(path)) {
            robot.getNavigation().stop();
            routeBlocked = true;
        }
    }

    @Override
    protected void attack(LivingEntity target, double squaredDistance) {
        if (eligible(target) && robot.getVisibilityCache().canSee(target)
                && squaredDistance <= getSquaredMaxAttackDistance(target)) {
            module.performAction(robot, RobotConfig.ATTACK_ENERGY_COST, () -> {
                if (!eligible(target) || !robot.getVisibilityCache().canSee(target)) {
                    return false;
                }
                boolean hit = robot.tryAttack(target);
                if (hit) {
                    lastHitTick = robot.getWorld().getTime();
                    robot.swingHand(Hand.MAIN_HAND);
                }
                return hit;
            });
        }
    }

    @Override
    public void stop() {
        LivingEntity previous = robot.getTarget();
        boolean finished = previous != null && !eligible(previous);
        super.stop();
        robot.getNavigation().stop();
        robot.setTarget(null);
        robot.clearModuleStatus();
        if (finished && robot.canWork() && !robot.isNearZoneCenter()) {
            robot.setReturningToZone(true);
        }
    }

    private boolean insideZone(Path path) {
        if (path == null) return false;
        for (int i = 0; i < path.getLength(); i++) {
            if (!robot.isInsideWorkZone(path.getNode(i).getBlockPos())) return false;
        }
        return true;
    }
}
