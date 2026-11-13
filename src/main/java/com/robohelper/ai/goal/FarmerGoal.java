package com.robohelper.ai.goal;

import com.robohelper.ai.WorkNavigation;
import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.module.FarmerModule;
import com.robohelper.screen.RobotStatus;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Серверная задача фермера: соседний подход, сбор и отдельная успешная посадка. */
public final class FarmerGoal extends Goal {
    private static final Map<Block, Item> SEEDS = Map.of(Blocks.WHEAT, Items.WHEAT_SEEDS,
            Blocks.CARROTS, Items.CARROT, Blocks.POTATOES, Items.POTATO, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
    private final RobotEntity robot;
    private final FarmerModule module;
    private final Map<BlockPos, Long> inaccessible = new HashMap<>();
    private BlockPos target;
    private BlockPos center;
    private BlockState cropState;
    private boolean harvested;
    private boolean hadSeeds;
    private Path path;
    private BlockPos stand;
    private long approachStarted;

    public FarmerGoal(RobotEntity robot, FarmerModule module) {
        this.robot = robot;
        this.module = module;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!robot.isOnGround() || robot.isStorageBlocked()) {
            return false;
        }
        if (target != null && !sameZone()) {
            finish();
        }
        if (target != null) {
            return true;
        }
        robot.setModuleStatus(RobotStatus.SEARCHING_CROP);
        if (!robot.tryBeginWorkScan(module.getId())) {
            return false;
        }
        long now = robot.getWorld().getTime();
        inaccessible.entrySet().removeIf(entry -> entry.getValue() <= now);
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos origin = robot.getZoneCenter();
        int radius = robot.getZoneRadius();
        for (BlockPos pos : BlockPos.iterate(origin.add(-radius, -RobotConfig.ZONE_VERTICAL_RADIUS, -radius),
                origin.add(radius, RobotConfig.ZONE_VERTICAL_RADIUS, radius))) {
            if (robot.isInsideWorkZone(pos) && robot.getWorld().isChunkLoaded(pos)
                    && !inaccessible.containsKey(pos) && mature(robot.getWorld().getBlockState(pos))) {
                candidates.add(pos.toImmutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> distance(pos)));
        for (BlockPos pos : candidates) {
            path = near(pos) ? null : WorkNavigation.findPathNear(robot, pos);
            if (!near(pos) && path == null) {
                inaccessible.put(pos, now + RobotConfig.WORK_PATH_TIMEOUT);
                continue;
            }
            target = pos;
            cropState = robot.getWorld().getBlockState(pos);
            center = robot.getZoneCenter();
            harvested = false;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        approachStarted = robot.getWorld().getTime();
        if (target != null && !near(target)) {
            if (path == null) {
                path = WorkNavigation.findPathNear(robot, target);
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
        return target != null && sameZone() && !robot.isStorageBlocked()
                && (near(target) || robot.getWorld().getTime() - approachStarted < RobotConfig.WORK_PATH_TIMEOUT);
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        robot.getNavigation().stop();
        path = null;
        if (target != null && !sameZone()) {
            finish();
        } else if (target != null && !near(target)
                && robot.getWorld().getTime() - approachStarted >= RobotConfig.WORK_PATH_TIMEOUT) {
            reject();
        }
    }

    @Override
    public void tick() {
        if (target == null || !sameZone() || robot.isStorageBlocked()) {
            return;
        }
        if (!near(target)) {
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
        if (!harvested) {
            robot.setModuleStatus(RobotStatus.HARVESTING);
            if (!robot.getWorld().getBlockState(target).equals(cropState) || !mature(cropState)) {
                finish();
                return;
            }
            module.performAction(robot, RobotConfig.HARVEST_ENERGY_COST, this::harvest);
        } else {
            plant();
        }
    }

    private boolean harvest() {
        if (!sameZone() || !robot.isInsideWorkZone(target) || !robot.getWorld().getBlockState(target).equals(cropState)) {
            return false;
        }
        // Т-14: семена должны быть запасены до сбора, а не получены тем же действием.
        hadSeeds = seedSlot() >= 0;
        ServerWorld world = (ServerWorld) robot.getWorld();
        List<ItemStack> drops = Block.getDroppedStacks(cropState, world, target, null, robot, new ItemStack(Items.IRON_HOE));
        if (!world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) {
            return false;
        }
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, target, Block.getRawIdFromState(cropState));
        harvested = true;
        robot.collectWorkDrops(drops);
        return true;
    }

    private void plant() {
        robot.setModuleStatus(RobotStatus.PLANTING_CROP);
        int slot = seedSlot();
        BlockState planted = ((CropBlock) cropState.getBlock()).withAge(0);
        if (!hadSeeds || slot < 0 || !robot.getWorld().getBlockState(target).isAir()
                || !robot.getWorld().getBlockState(target.down()).isOf(Blocks.FARMLAND)
                || !planted.canPlaceAt(robot.getWorld(), target)) {
            finish();
            return;
        }
        module.performAction(robot, RobotConfig.PLANT_ENERGY_COST, () -> {
            if (!sameZone() || !robot.isInsideWorkZone(target) || !robot.getWorld().getBlockState(target).isAir()
                    || !planted.canPlaceAt(robot.getWorld(), target)
                    || !robot.getWorld().setBlockState(target, planted, Block.NOTIFY_ALL)) {
                return false;
            }
            robot.getInventory().getStack(slot).decrement(1);
            robot.getInventory().markDirty();
            finish();
            return true;
        });
    }

    private int seedSlot() {
        Item seed = cropState == null ? null : SEEDS.get(cropState.getBlock());
        for (int slot = 0; slot < RobotConfig.STORAGE_SLOTS; slot++) {
            if (robot.getInventory().getStack(slot).isOf(seed)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean mature(BlockState state) {
        return SEEDS.containsKey(state.getBlock()) && ((CropBlock) state.getBlock()).isMature(state);
    }

    private double distance(BlockPos pos) {
        return robot.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private boolean near(BlockPos pos) {
        return distance(pos) <= RobotConfig.WORK_REACH * RobotConfig.WORK_REACH;
    }

    private boolean sameZone() {
        return center != null && center.equals(robot.getZoneCenter()) && robot.isInsideWorkZone(target);
    }

    private void reject() {
        if (target != null) {
            inaccessible.put(target, robot.getWorld().getTime() + RobotConfig.WORK_PATH_TIMEOUT);
        }
        finish();
    }

    private void finish() {
        target = null;
        path = null;
        stand = null;
        robot.getNavigation().stop();
    }
}
