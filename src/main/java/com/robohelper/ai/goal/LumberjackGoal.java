package com.robohelper.ai.goal;

import com.robohelper.ai.TreeFinder;
import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.module.LumberjackModule;
import com.robohelper.screen.RobotStatus;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SaplingBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.world.WorldEvents;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Подход, рубка сверху вниз и посадка с паузами, проверками состояния и границ. */
public final class LumberjackGoal extends Goal {
    private enum Phase { APPROACH, CUT, PLANT }
    private final RobotEntity robot;
    private final LumberjackModule module;
    private final Map<BlockPos, Long> inaccessible = new HashMap<>();
    private final ArrayDeque<BlockPos> remaining = new ArrayDeque<>();
    private TreeFinder.Tree tree;
    private BlockPos selectedCenter;
    private Phase phase;
    private Path preparedPath;
    private BlockPos standTarget;
    private long approachStarted;

    public LumberjackGoal(RobotEntity robot, LumberjackModule module) {
        this.robot = robot;
        this.module = module;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        // В первый тик после появления навигация ещё не готова: null не означает недоступное дерево.
        if (!robot.isOnGround() || robot.isStorageBlocked()) {
            return false;
        }
        if (tree != null && !sameZone()) {
            finish();
        }
        if (tree != null) {
            return true;
        }
        robot.setModuleStatus(RobotStatus.SEARCHING_TREE);
        if (!robot.tryBeginWorkScan()) {
            return false;
        }
        long now = robot.getWorld().getTime();
        inaccessible.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (BlockPos root : TreeFinder.findRoots(robot)) {
            if (inaccessible.containsKey(root)) {
                continue;
            }
            TreeFinder.Tree candidate = TreeFinder.inspect(robot, root);
            if (candidate == null) {
                continue;
            }
            preparedPath = near(root) ? null : pathToTree(root);
            if (!near(root) && (preparedPath == null || !preparedPath.reachesTarget())) {
                inaccessible.put(root, now + RobotConfig.UNREACHABLE_TREE_TICKS);
                continue;
            }
            tree = candidate;
            selectedCenter = robot.getZoneCenter();
            remaining.addAll(tree.topDown());
            phase = Phase.APPROACH;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        if (tree == null) {
            return;
        }
        if (phase == Phase.APPROACH) {
            approachStarted = robot.getWorld().getTime();
            if (near(tree.root())) {
                phase = Phase.CUT;
                robot.getNavigation().stop();
            } else {
                // При возобновлении FOLLOW мог изменить положение робота.
                if (preparedPath == null) {
                    preparedPath = pathToTree(tree.root());
                }
                if (preparedPath == null || !preparedPath.reachesTarget()
                        || !robot.getNavigation().startMovingAlong(preparedPath, RobotConfig.NAVIGATION_SPEED)) {
                    rejectPath();
                } else {
                    standTarget = preparedPath.getEnd().getBlockPos();
                    preparedPath = null;
                }
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return tree != null && sameZone() && !robot.isStorageBlocked()
                && (phase != Phase.APPROACH
                    || robot.getWorld().getTime() - approachStarted < RobotConfig.WORK_PATH_TIMEOUT);
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        robot.getNavigation().stop();
        preparedPath = null;
        if (tree != null && (!sameZone() || phase == Phase.APPROACH
                && robot.getWorld().getTime() - approachStarted >= RobotConfig.WORK_PATH_TIMEOUT)) {
            if (sameZone()) {
                rejectPath();
            } else {
                finish();
            }
        }
        robot.setModuleStatus(RobotStatus.WORKING);
    }

    @Override
    public void tick() {
        if (tree == null || !sameZone() || robot.isStorageBlocked()) {
            return;
        }
        if (phase == Phase.APPROACH) {
            robot.setModuleStatus(RobotStatus.SEARCHING_TREE);
            if (near(tree.root())) {
                phase = Phase.CUT;
                robot.getNavigation().stop();
            } else {
                if (robot.getNavigation().isIdle()) {
                    if (standTarget != null && robot.squaredDistanceTo(standTarget.getX() + 0.5,
                            standTarget.getY(), standTarget.getZ() + 0.5) <= 1.0) {
                        robot.getMoveControl().moveTo(standTarget.getX() + 0.5, standTarget.getY(),
                                standTarget.getZ() + 0.5, RobotConfig.NAVIGATION_SPEED);
                    } else {
                        rejectPath();
                    }
                }
                return;
            }
        }
        if (!near(tree.root())) {
            phase = Phase.APPROACH;
            start();
            return;
        }
        if (phase == Phase.CUT) {
            robot.setModuleStatus(RobotStatus.CHOPPING_TREE);
            while (!remaining.isEmpty()) {
                BlockPos pos = remaining.peekFirst();
                if (!robot.isInsideWorkZone(pos) || !robot.getWorld().isChunkLoaded(pos)) {
                    finish();
                    return;
                }
                if (!robot.getWorld().getBlockState(pos).equals(tree.blocks().get(pos))) {
                    remaining.removeFirst();
                    continue;
                }
                module.performAction(robot, RobotConfig.CHOP_ENERGY_COST, () -> chop(pos));
                return;
            }
            phase = Phase.PLANT;
        }
        if (phase == Phase.PLANT) {
            robot.setModuleStatus(RobotStatus.PLANTING_TREE);
            plant();
        }
    }

    private boolean chop(BlockPos pos) {
        ServerWorld world = (ServerWorld) robot.getWorld();
        BlockState state = world.getBlockState(pos);
        if (!robot.isInsideWorkZone(pos) || !state.equals(tree.blocks().get(pos)) || !state.isIn(BlockTags.LOGS)) {
            return false;
        }
        ItemStack virtualTool = new ItemStack(Items.IRON_AXE);
        List<ItemStack> drops = Block.getDroppedStacks(state, world, pos, world.getBlockEntity(pos), robot, virtualTool);
        if (!world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) {
            return false;
        }
        remaining.removeFirst();
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
        for (ItemStack drop : drops) {
            ItemStack excess = robot.getInventory().insertStorage(drop);
            if (!excess.isEmpty()) {
                robot.dropStack(excess);
                robot.blockWorkForStorage(excess);
            }
        }
        return true;
    }

    private void plant() {
        BlockPos root = tree.root();
        if (!robot.isInsideWorkZone(root) || !robot.getWorld().isChunkLoaded(root)
                || !robot.getWorld().getBlockState(root).isAir()) {
            finish();
            return;
        }
        for (int slot = 0; slot < RobotConfig.STORAGE_SLOTS; slot++) {
            ItemStack stack = robot.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem item) || !(item.getBlock() instanceof SaplingBlock)) {
                continue;
            }
            BlockState sapling = item.getBlock().getDefaultState();
            if (!sapling.canPlaceAt(robot.getWorld(), root)) {
                continue;
            }
            module.performAction(robot, RobotConfig.PLANT_ENERGY_COST, () -> {
                if (!robot.getWorld().getBlockState(root).isAir() || !sapling.canPlaceAt(robot.getWorld(), root)
                        || !robot.getWorld().setBlockState(root, sapling, Block.NOTIFY_ALL)) {
                    return false;
                }
                stack.decrement(1);
                robot.getInventory().markDirty();
                finish();
                return true;
            });
            return;
        }
        finish();
    }

    /** Ищет доступное место в радиусе рубки; все узлы пути также остаются в зоне. */
    private Path pathToTree(BlockPos root) {
        Set<BlockPos> targets = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz > RobotConfig.TREE_STAND_RADIUS * RobotConfig.TREE_STAND_RADIUS) {
                        continue;
                    }
                    BlockPos pos = root.add(dx, dy, dz);
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

    private boolean near(BlockPos pos) {
        return robot.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)
                <= RobotConfig.TREE_REACH * RobotConfig.TREE_REACH;
    }

    private boolean sameZone() {
        return selectedCenter != null && selectedCenter.equals(robot.getZoneCenter());
    }

    private void rejectPath() {
        if (tree != null) {
            inaccessible.put(tree.root(), robot.getWorld().getTime() + RobotConfig.UNREACHABLE_TREE_TICKS);
        }
        finish();
    }

    private void finish() {
        tree = null;
        preparedPath = null;
        standTarget = null;
        remaining.clear();
        robot.getNavigation().stop();
    }
}
