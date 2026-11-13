package com.robohelper.ai;

import net.minecraft.block.Blocks;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/** Наземные пути робота обходят грядки, включая маршрут FOLLOW и возвращение. */
public final class RobotNavigation extends MobNavigation {
    public RobotNavigation(MobEntity entity, World world) {
        super(entity, world);
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        nodeMaker = new LandPathNodeMaker() {
            @Override
            public PathNodeType getDefaultNodeType(BlockView world, int x, int y, int z) {
                if (world.getBlockState(new BlockPos(x, y - 1, z)).isOf(Blocks.FARMLAND)
                        || world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.FARMLAND)) {
                    return PathNodeType.BLOCKED;
                }
                return super.getDefaultNodeType(world, x, y, z);
            }
        };
        nodeMaker.setCanEnterOpenDoors(true);
        return new PathNodeNavigator(nodeMaker, range);
    }
}
