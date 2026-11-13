package com.robohelper.module;

import com.robohelper.ai.goal.FarmerGoal;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Identifier;
import java.util.List;

/** Фермер собирает только спелые ванильные культуры и пересаживает запасённые семена. */
public final class FarmerModule extends AbstractModule {
    public static final Identifier ID = new Identifier("robohelper", "farmer");

    public FarmerModule() {
        super(ID);
    }

    @Override
    public List<Goal> createGoals(RobotEntity robot) {
        return List.of(new FarmerGoal(robot, this));
    }
}
