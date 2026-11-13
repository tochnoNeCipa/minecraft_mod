package com.robohelper.module;

import com.robohelper.entity.RobotEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import java.util.List;

/** Контракт профессии: сущность подключает её через реестр, не зная конкретного класса. */
public interface RobotModule {
    /** Устойчивый идентификатор профессии в реестре. */
    Identifier getId();
    /** Дополнительная инициализация после подключения целей. */
    void onInstall(RobotEntity robot);
    /** Освобождение состояния после остановки и снятия целей. */
    void onRemove(RobotEntity robot);
    /** Создаёт отдельные экземпляры целей для данного робота. */
    List<Goal> createGoals(RobotEntity robot);
    /** Читает уровень из NBT предмета. */
    int getLevel(ItemStack stack);
    /** Возвращает интервал между успешными действиями в тиках. */
    int getActionInterval(ItemStack stack);
    /** Применяет скидку уровня к базовой стоимости. */
    int getEnergyCost(ItemStack stack, int base);

    /** Группа приоритета из ТЗ: охрана 2, логистика 3, производство 4. */
    default int getPriority() {
        return 4;
    }
}
