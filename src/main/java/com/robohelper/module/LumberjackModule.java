package com.robohelper.module;

import com.robohelper.ai.goal.LumberjackGoal;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Identifier;
import java.util.List;

/** Профессия лесоруба подключается одной фабрикой без изменения сущности. */
public final class LumberjackModule extends AbstractModule {
    public static final Identifier ID = new Identifier("robohelper", "lumberjack");

    public LumberjackModule() {
        super(ID);
    }

    @Override
    public List<Goal> createGoals(RobotEntity robot) {
        return List.of(new LumberjackGoal(robot, this));
    }
}
