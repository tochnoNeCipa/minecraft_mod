package com.robohelper.ai;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Подход к соседнему свободному месту без выхода пути за пределы рабочей зоны. */
public final class WorkNavigation {
    private WorkNavigation() {
    }

    /** Выбирает свободный соседний узел и проверяет весь путь зоной. */
    @Nullable
    public static Path findPathNear(RobotEntity robot, BlockPos target) {
        Set<BlockPos> targets = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz > RobotConfig.TREE_STAND_RADIUS * RobotConfig.TREE_STAND_RADIUS) {
                        continue;
                    }
                    BlockPos pos = target.add(dx, dy, dz);
                    if (!robot.isInsideWorkZone(pos) || !robot.getWorld().isChunkLoaded(pos)
                            || !robot.getWorld().getFluidState(pos).isEmpty()
                            || !robot.getWorld().getBlockState(pos.down())
                                    .isSideSolidFullSquare(robot.getWorld(), pos.down(), Direction.UP)) {
                        continue;
                    }
                    Box box = robot.getBoundingBox().offset(pos.getX() + 0.5 - robot.getX(),
                            pos.getY() - robot.getY(), pos.getZ() + 0.5 - robot.getZ());
                    if (robot.getWorld().isSpaceEmpty(robot, box)) {
                        targets.add(pos);
                    }
                }
            }
        }
        if (targets.isEmpty()) {
            return null;
        }
        Path path = robot.getNavigation().findPathTo(targets, 0);
        if (path == null || !path.reachesTarget()) {
            return null;
        }
        for (int node = 0; node < path.getLength(); node++) {
            if (!robot.isInsideWorkZone(path.getNode(node).getBlockPos())) {
                return null;
            }
        }
        return path;
    }
}
