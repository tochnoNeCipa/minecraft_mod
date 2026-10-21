package com.robohelper.item;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.registry.ModEntities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Корпус создаёт одного робота на свободном месте рядом с выбранной гранью блока. */
public final class RobotSpawnItem extends Item {
    public RobotSpawnItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null || !player.canModifyBlocks()) {
            return ActionResult.FAIL;
        }
        BlockPos pos = context.getBlockPos();
        if (!world.getBlockState(pos).isReplaceable()) {
            pos = pos.offset(context.getSide());
        }
        if (!world.isInBuildLimit(pos) || pos.getY() + RobotConfig.HEIGHT > world.getTopY()
                || !world.canPlayerModifyAt(player, pos)) {
            return ActionResult.FAIL;
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        RobotEntity robot = ModEntities.ROBOT.create(world);
        if (robot == null) {
            return ActionResult.FAIL;
        }
        robot.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                context.getPlayerYaw(), 0);
        robot.setOwnerUuid(player.getUuid());
        if (!world.getWorldBorder().contains(robot.getBoundingBox())
                || !world.isSpaceEmpty(robot) || !world.spawnEntity(robot)) {
            return ActionResult.FAIL;
        }
        if (context.getStack().hasCustomName()) {
            robot.setCustomName(context.getStack().getName());
        }
        if (!player.getAbilities().creativeMode) {
            context.getStack().decrement(1);
        }
        return ActionResult.CONSUME;
    }
}
