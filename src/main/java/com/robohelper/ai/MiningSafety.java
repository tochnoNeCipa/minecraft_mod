package com.robohelper.ai;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Проверка целого сечения до разрушения любого из двух блоков тоннеля. */
public final class MiningSafety {
    private MiningSafety() { }

    public enum Result { SAFE, PROTECTED, LIQUID, DROP, UNLOADED }

    /** Проверяет блоки, соседние жидкости и опору, не загружая отсутствующие чанки. */
    public static Result inspect(RobotEntity robot, BlockPos feet) {
        for (BlockPos cell : new BlockPos[]{feet, feet.up()}) {
            if (!robot.getWorld().isChunkLoaded(cell)) {
                return Result.UNLOADED;
            }
            BlockState state = robot.getWorld().getBlockState(cell);
            if (!state.isAir() && (state.hasBlockEntity() || state.isOf(Blocks.SPAWNER)
                    || state.getHardness(robot.getWorld(), cell) < 0
                    || RobotConfig.MINER_BLACKLIST.contains(state.getBlock())
                    || state.isToolRequired() && !new ItemStack(Items.IRON_PICKAXE).isSuitableFor(state))) {
                return Result.PROTECTED;
            }
            if (!robot.getWorld().getFluidState(cell).isEmpty()) {
                return Result.LIQUID;
            }
            for (Direction side : Direction.values()) {
                BlockPos neighbor = cell.offset(side);
                if (!robot.getWorld().isChunkLoaded(neighbor)) {
                    return Result.UNLOADED;
                }
                if (!robot.getWorld().getFluidState(neighbor).isEmpty()) {
                    return Result.LIQUID;
                }
            }
        }
        for (int depth = 1; depth <= RobotConfig.MINER_MAX_DROP + 1; depth++) {
            BlockPos floor = feet.down(depth);
            if (!robot.getWorld().getFluidState(floor).isEmpty()) {
                return Result.LIQUID;
            }
            if (robot.getWorld().getBlockState(floor).isSideSolidFullSquare(robot.getWorld(), floor, Direction.UP)) {
                return Result.SAFE;
            }
        }
        return Result.DROP;
    }
}
