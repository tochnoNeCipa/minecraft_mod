package com.robohelper.ai.goal;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/** Возвращает робота к центру зоны перед запуском рабочих профессий. */
public final class ReturnToZoneGoal extends Goal {
    private final RobotEntity robot;
    private int nextAttempt;

    public ReturnToZoneGoal(RobotEntity robot) {
        this.robot = robot;
        setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return robot.canWork() && robot.requiresZoneReturn() && robot.age >= nextAttempt;
    }

    @Override
    public void start() {
        robot.setReturningToZone(true);
        BlockPos center = robot.getZoneCenter();
        robot.getNavigation().startMovingTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5,
                RobotConfig.NAVIGATION_SPEED);
        nextAttempt = robot.age + RobotConfig.RETURN_RETRY_INTERVAL;
    }

    @Override
    public boolean shouldContinue() {
        return robot.canWork() && !robot.isNearZoneCenter() && !robot.getNavigation().isIdle();
    }

    @Override
    public void stop() {
        robot.getNavigation().stop();
        if (robot.isNearZoneCenter()) {
            robot.setReturningToZone(false);
        }
    }
}
