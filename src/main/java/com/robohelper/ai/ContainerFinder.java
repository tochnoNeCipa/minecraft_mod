package com.robohelper.ai;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Поиск только сундуков и бочек; обе половины двойного сундука должны быть в зоне. */
public final class ContainerFinder {
    private ContainerFinder() {
    }

    /** Возвращает положения контейнеров в порядке расстояния от робота. */
    public static List<BlockPos> find(RobotEntity robot) {
        List<BlockPos> containers = new ArrayList<>();
        BlockPos center = robot.getZoneCenter();
        int radius = robot.getZoneRadius();
        for (BlockPos pos : BlockPos.iterate(center.add(-radius, -RobotConfig.ZONE_VERTICAL_RADIUS, -radius),
                center.add(radius, RobotConfig.ZONE_VERTICAL_RADIUS, radius))) {
            if (robot.isInsideWorkZone(pos) && robot.getWorld().isChunkLoaded(pos)) {
                BlockState state = robot.getWorld().getBlockState(pos);
                if (state.getBlock() instanceof ChestBlock || state.isOf(Blocks.BARREL)) {
                    containers.add(pos.toImmutable());
                }
            }
        }
        containers.sort(Comparator.comparingDouble(pos -> robot.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
        return containers;
    }

    /** Повторно проверяет существование, доступность и границы перед вставкой. */
    @Nullable
    public static Inventory resolve(RobotEntity robot, BlockPos pos) {
        if (pos == null || !robot.isInsideWorkZone(pos) || !robot.getWorld().isChunkLoaded(pos)) {
            return null;
        }
        BlockState state = robot.getWorld().getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                BlockPos partner = pos.offset(ChestBlock.getFacing(state));
                if (!robot.isInsideWorkZone(partner) || !robot.getWorld().isChunkLoaded(partner)) {
                    return null;
                }
            }
            return ChestBlock.getInventory(chest, state, robot.getWorld(), pos, false);
        }
        return state.isOf(Blocks.BARREL) && robot.getWorld().getBlockEntity(pos) instanceof Inventory inventory
                ? inventory : null;
    }
}
