package com.robohelper.module;

import com.robohelper.ai.goal.LogisticsGoal;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Identifier;
import java.util.List;

/** Логист прерывает производство и выгружает груз, сохраняя запас для посадки. */
public final class LogisticsModule extends AbstractModule {
    public static final Identifier ID = new Identifier("robohelper", "logistics");

    public LogisticsModule() {
        super(ID);
    }

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public List<Goal> createGoals(RobotEntity robot) {
        return List.of(new LogisticsGoal(robot, this));
    }
}
