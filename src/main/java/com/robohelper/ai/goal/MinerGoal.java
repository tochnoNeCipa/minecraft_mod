package com.robohelper.ai.goal;

import com.robohelper.ai.MiningSafety;
import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.module.MinerModule;
import com.robohelper.screen.RobotStatus;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldEvents;
import java.util.EnumSet;
import java.util.List;

/** Прямой тоннель: проверка обоих блоков, голова, ноги, переход к следующему сечению. */
public final class MinerGoal extends Goal {
    private final RobotEntity robot;
    private final MinerModule module;
    private NbtCompound plan;
    private BlockPos feet;
    private boolean paused;
    private boolean active;
    private long nextAttempt;
    private long movementStarted;

    public MinerGoal(RobotEntity robot, MinerModule module) {
        this.robot = robot;
        this.module = module;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!robot.isOnGround() || robot.isStorageBlocked() || robot.getWorld().getTime() < nextAttempt) {
            return false;
        }
        ItemStack stack = robot.getModules().getStack(module);
        NbtCompound tag = stack.getOrCreateNbt();
        plan = tag.getCompound(MinerModule.PLAN_KEY);
        if (!validPlan()) {
            plan = new NbtCompound();
            plan.putLong("Origin", robot.getBlockPos().asLong());
            plan.putLong("Zone", robot.getZoneCenter().asLong());
            plan.putInt("Command", robot.getMinerCommand());
            plan.putString("Direction", robot.getMinerDirection().getName());
            plan.putInt("Step", 1);
            tag.put(MinerModule.PLAN_KEY, plan);
        }
        if (plan.getBoolean("Done")) {
            robot.setModuleStatus(RobotStatus.TUNNEL_COMPLETE);
            return false;
        }
        feet = BlockPos.fromLong(plan.getLong("Origin")).offset(robot.getMinerDirection(), plan.getInt("Step"));
        if (!robot.isInsideWorkZone(feet) || !robot.isInsideWorkZone(feet.up())
                || plan.getInt("Step") > robot.getZoneRadius() * 2 + 1) {
            plan.putBoolean("Done", true);
            robot.setModuleStatus(RobotStatus.TUNNEL_COMPLETE);
            return false;
        }
        paused = false;
        return safe();
    }

    private boolean validPlan() {
        return plan != null && plan.contains("Origin", NbtElement.NUMBER_TYPE)
                && plan.getLong("Zone") == robot.getZoneCenter().asLong()
                && plan.getInt("Command") == robot.getMinerCommand()
                && plan.getString("Direction").equals(robot.getMinerDirection().getName())
                && plan.getInt("Step") >= 1 && plan.getInt("Step") <= RobotConfig.ZONE_RADIUS * 2 + 2;
    }

    @Override
    public void start() {
        active = true;
        movementStarted = robot.getWorld().getTime();
    }

    @Override
    public boolean shouldContinue() {
        return !paused && validPlan() && !plan.getBoolean("Done") && !robot.isStorageBlocked();
    }

    @Override
    public boolean shouldRunEveryTick() { return true; }

    @Override
    public void stop() {
        active = false;
        robot.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (paused) {
            return;
        }
        if (!validPlan()) {
            robot.getNavigation().stop();
            paused = true;
            return;
        }
        if (!safe()) {
            return;
        }
        robot.setModuleStatus(RobotStatus.MINING);
        double distance = robot.squaredDistanceTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        if (distance > RobotConfig.WORK_REACH * RobotConfig.WORK_REACH) {
            moveTo(feet.offset(robot.getMinerDirection().getOpposite()));
            return;
        }
        BlockPos target = !robot.getWorld().getBlockState(feet.up()).isAir() ? feet.up() : feet;
        if (!robot.getWorld().getBlockState(target).isAir()) {
            robot.getNavigation().stop();
            module.performAction(robot, RobotConfig.MINE_ENERGY_COST, () -> mine(target));
        } else if (distance <= RobotConfig.MINER_ARRIVAL_DISTANCE * RobotConfig.MINER_ARRIVAL_DISTANCE) {
            plan.putInt("Step", plan.getInt("Step") + 1);
            paused = true; // Следующее сечение выбирается через canStart, без второго действия в тик.
        } else {
            moveTo(feet);
        }
    }

    private boolean mine(BlockPos target) {
        if (!validPlan() || !robot.isInsideWorkZone(target) || !safe()) {
            return false;
        }
        BlockState state = robot.getWorld().getBlockState(target);
        if (state.isAir()) {
            return false;
        }
        ServerWorld world = (ServerWorld) robot.getWorld();
        List<ItemStack> drops = Block.getDroppedStacks(state, world, target, null, robot, new ItemStack(Items.IRON_PICKAXE));
        if (!world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL)) {
            return false;
        }
        world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, target, Block.getRawIdFromState(state));
        robot.collectWorkDrops(drops);
        movementStarted = world.getTime();
        return true;
    }

    private boolean safe() {
        MiningSafety.Result result = MiningSafety.inspect(robot, feet);
        if (result == MiningSafety.Result.SAFE) {
            return true;
        }
        if (active) {
            robot.getNavigation().stop();
        }
        robot.setModuleStatus(RobotStatus.MINING_BLOCKED);
        robot.notifyWorkOwner("message.robohelper.miner_" + result.name().toLowerCase(java.util.Locale.ROOT));
        paused = true;
        nextAttempt = robot.getWorld().getTime() + RobotConfig.WORK_SCAN_INTERVAL;
        return false;
    }

    private void moveTo(BlockPos destination) {
        if (robot.getWorld().getTime() - movementStarted >= RobotConfig.WORK_PATH_TIMEOUT) {
            paused = true;
            nextAttempt = robot.getWorld().getTime() + RobotConfig.WORK_SCAN_INTERVAL;
            robot.setModuleStatus(RobotStatus.MINING_BLOCKED);
            return;
        }
        if (!robot.getNavigation().isIdle()) {
            return;
        }
        Path path = robot.getNavigation().findPathTo(destination, 0);
        if (path != null && path.reachesTarget()) {
            for (int i = 0; i < path.getLength(); i++) {
                if (!robot.isInsideWorkZone(path.getNode(i).getBlockPos())) {
                    return;
                }
            }
            robot.getNavigation().startMovingAlong(path, RobotConfig.NAVIGATION_SPEED);
        }
    }
}
