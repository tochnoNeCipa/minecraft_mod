package com.robohelper.module;

import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import java.util.List;

/** Контракт профессии: сущность подключает её через реестр, не зная конкретного класса. */
public interface RobotModule {
    Identifier getId();
    void onInstall(RobotEntity robot);
    void onRemove(RobotEntity robot);
    List<Goal> createGoals(RobotEntity robot);
    int getLevel(ItemStack stack);
    int getActionInterval(ItemStack stack);
    int getEnergyCost(ItemStack stack, int base);

    /** Группа приоритета из ТЗ: охрана 2, логистика 3, производство 4. */
    default int getPriority() {
        return 4;
    }
}
