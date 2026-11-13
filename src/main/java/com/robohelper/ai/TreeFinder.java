package com.robohelper.ai;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Поиск оснований и ограниченный BFS по 26 соседям только внутри рабочей зоны. */
public final class TreeFinder {
    private TreeFinder() {
    }

    /** Возвращает основания в порядке расстояния от робота; неподгруженные чанки не читает. */
    public static List<BlockPos> findRoots(RobotEntity robot) {
        List<BlockPos> roots = new ArrayList<>();
        BlockPos center = robot.getZoneCenter();
        int radius = robot.getZoneRadius();
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -RobotConfig.ZONE_VERTICAL_RADIUS, -radius),
                center.add(radius, RobotConfig.ZONE_VERTICAL_RADIUS, radius))) {
            if (robot.isInsideWorkZone(pos) && robot.getWorld().isChunkLoaded(pos)
                    && robot.getWorld().getBlockState(pos).isIn(BlockTags.LOGS)
                    && robot.getWorld().getBlockState(pos.down()).isIn(BlockTags.DIRT)) {
                roots.add(pos.toImmutable());
            }
        }
        roots.sort(Comparator.comparingDouble(pos -> robot.squaredDistanceTo(pos.getX() + 0.5,
                pos.getY(), pos.getZ() + 0.5)));
        return roots;
    }

    /** Не считает брёвна без естественной листвы деревом; группы больше лимита отклоняет целиком. */
    @Nullable
    public static Tree inspect(RobotEntity robot, BlockPos root) {
        if (!robot.isInsideWorkZone(root) || !robot.getWorld().isChunkLoaded(root)
                || !robot.getWorld().getBlockState(root).isIn(BlockTags.LOGS)
                || !robot.getWorld().getBlockState(root.down()).isIn(BlockTags.DIRT)) {
            return null;
        }
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        blocks.put(root.toImmutable(), robot.getWorld().getBlockState(root));
        queue.add(root.toImmutable());
        boolean naturalLeaves = false;
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (blocks.get(current).hasBlockEntity()) {
                return null;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos neighbor = current.add(dx, dy, dz);
                        if (!robot.isInsideWorkZone(neighbor) || !robot.getWorld().isChunkLoaded(neighbor)) {
                            continue;
                        }
                        BlockState state = robot.getWorld().getBlockState(neighbor);
                        if (state.isIn(BlockTags.LOGS) && !blocks.containsKey(neighbor)) {
                            blocks.put(neighbor, state);
                            if (blocks.size() > RobotConfig.MAX_TREE_BLOCKS) {
                                return null;
                            }
                            queue.addLast(neighbor);
                        } else if (state.getBlock() instanceof LeavesBlock && !state.get(LeavesBlock.PERSISTENT)) {
                            naturalLeaves = true;
                        }
                    }
                }
            }
        }
        if (!naturalLeaves) {
            return null;
        }
        List<BlockPos> topDown = new ArrayList<>(blocks.keySet());
        topDown.sort(Comparator.comparingInt(BlockPos::getY).reversed()
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return new Tree(root.toImmutable(), Map.copyOf(blocks), List.copyOf(topDown));
    }

    /** Снимок защищает от рубки блока другого типа, появившегося после выбора дерева. */
    public record Tree(BlockPos root, Map<BlockPos, BlockState> blocks, List<BlockPos> topDown) {
    }
}
