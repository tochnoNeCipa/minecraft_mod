package com.robohelper.module;

import com.robohelper.ai.goal.GuardGoal;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.Identifier;
import java.util.List;

/** Охрана имеет приоритет над производством и логистикой, но соблюдает общие паузы. */
public final class GuardModule extends AbstractModule {
    public static final Identifier ID = new Identifier("robohelper", "guard");

    public GuardModule() { super(ID); }

    @Override
    public int getPriority() { return 2; }

    @Override
    public List<Goal> createGoals(RobotEntity robot) {
        return List.of(new GuardGoal(robot, this));
    }

    @Override
    public void onRemove(RobotEntity robot) {
        robot.setTarget(null);
        robot.setAttacking(false);
    }
}
