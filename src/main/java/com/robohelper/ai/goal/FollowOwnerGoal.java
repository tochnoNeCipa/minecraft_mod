package com.robohelper.ai.goal;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotMode;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/** Бесплатное следование за владельцем в том же измерении и безопасная телепортация. */
public final class FollowOwnerGoal extends Goal {
    private final RobotEntity robot;
    private PlayerEntity owner;
    private int repathTicks;

    public FollowOwnerGoal(RobotEntity robot) {
        this.robot = robot;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        owner = robot.getOwnerPlayer();
        return validOwner() && robot.getMode() == RobotMode.FOLLOW
                && robot.squaredDistanceTo(owner) > square(RobotConfig.FOLLOW_START_DISTANCE);
    }

    @Override
    public boolean shouldContinue() {
        return validOwner() && robot.getMode() == RobotMode.FOLLOW
                && robot.squaredDistanceTo(owner) > square(RobotConfig.FOLLOW_STOP_DISTANCE);
    }

    private boolean validOwner() {
        return owner != null && robot.isOwner(owner) && owner.isAlive() && !owner.isSpectator()
                && owner.getWorld() == robot.getWorld();
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        robot.getNavigation().stop();
        owner = null;
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (!validOwner()) {
            return;
        }
        robot.getLookControl().lookAt(owner, 10.0F, robot.getMaxLookPitchChange());
        if (repathTicks-- > 0) {
            return;
        }
        repathTicks = RobotConfig.FOLLOW_REPATH_INTERVAL;
        if (robot.squaredDistanceTo(owner) > square(RobotConfig.FOLLOW_TELEPORT_DISTANCE)
                && tryTeleportNearOwner()) {
            return;
        }
        robot.getNavigation().startMovingTo(owner, RobotConfig.NAVIGATION_SPEED);
    }

    private boolean tryTeleportNearOwner() {
        BlockPos center = owner.getBlockPos();
        int radius = RobotConfig.TELEPORT_RADIUS;
        for (int dy = 0; dy <= 2; dy++) {
            int yOffset = dy == 2 ? -1 : dy;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    BlockPos target = center.add(dx, yOffset, dz);
                    if (isSafeTeleportTarget(target)) {
                        robot.requestTeleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
                        robot.setVelocity(Vec3d.ZERO);
                        robot.getNavigation().stop();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isSafeTeleportTarget(BlockPos target) {
        World world = robot.getWorld();
        if (!world.isChunkLoaded(target) || !world.isInBuildLimit(target)
                || target.getY() + robot.getHeight() > world.getTopY()
                || !world.getFluidState(target).isEmpty() || !world.getFluidState(target.up()).isEmpty()
                || !world.getFluidState(target.down()).isEmpty()
                || !world.getBlockState(target.down()).isSideSolidFullSquare(world, target.down(), Direction.UP)) {
            return false;
        }
        Box box = robot.getBoundingBox().offset(target.getX() + 0.5 - robot.getX(),
                target.getY() - robot.getY(), target.getZ() + 0.5 - robot.getZ());
        return world.getWorldBorder().contains(box) && world.isSpaceEmpty(robot, box)
                && LandPathNodeMaker.getLandNodeType(world, target.mutableCopy()) == PathNodeType.WALKABLE;
    }

    private static double square(double value) {
        return value * value;
    }
}
