package com.robohelper.module;

import com.robohelper.ai.goal.MinerGoal;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Identifier;
import java.util.List;

/** Шахтёр сохраняет задание в предмете и продолжает его после выгрузки и загрузки мира. */
public final class MinerModule extends AbstractModule {
    public static final Identifier ID = new Identifier("robohelper", "miner");
    public static final String PLAN_KEY = "RoboHelper.Mining";

    @Override
    public void onInstall(RobotEntity robot) {
        if (!robot.getModules().getStack(this).getOrCreateNbt().contains(PLAN_KEY)) {
            robot.setMinerDirection(robot.getHorizontalFacing());
        }
    }

    public MinerModule() {
        super(ID);
    }

    @Override
    public List<Goal> createGoals(RobotEntity robot) {
        return List.of(new MinerGoal(robot, this));
    }
}
