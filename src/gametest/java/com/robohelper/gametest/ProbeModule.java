package com.robohelper.gametest;

import com.robohelper.module.AbstractModule;
import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.List;
import java.util.EnumSet;

/** Тестовая профессия создаёт палку за действие и проверяет общий каркас поведения. */
public final class ProbeModule extends AbstractModule {
    public ProbeModule() {
        super(TestContent.PROBE_ID);
    }

    @Override
    public void onInstall(RobotEntity robot) {
        robot.addCommandTag("probe-installed");
        robot.getCommandTags().remove("probe-removed");
    }

    @Override
    public void onRemove(RobotEntity robot) {
        robot.getCommandTags().remove("probe-installed");
        robot.addCommandTag("probe-removed");
    }

    @Override
    public List<Goal> createGoals(RobotEntity robot) {
        return List.of(new Goal() {
            { setControls(EnumSet.of(Control.MOVE)); }
            @Override
            public boolean canStart() { return true; }
            @Override
            public boolean shouldRunEveryTick() { return true; }
            @Override
            public void tick() {
                performAction(robot, 15, () -> robot.getInventory().insertStorage(new ItemStack(Items.STICK)).isEmpty());
            }
        });
    }
}
