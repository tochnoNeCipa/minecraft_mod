package com.robohelper.gametest;

import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.AbstractModule;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;

/** Реальная тестовая профессия проверяет жизненный цикл и учёт действий на сервере. */
public final class ModuleTests implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void installFromHandAndRemoveStopsGoals(TestContext context) {
        RobotEntity robot = robot(context);
        PlayerEntity owner = context.createMockSurvivalPlayer();
        robot.setOwnerUuid(owner.getUuid());
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(TestContent.PROBE));
        robot.interact(owner, Hand.MAIN_HAND);
        context.assertTrue(owner.getMainHandStack().isEmpty(), "Установка расходует один модуль");
        context.assertTrue(robot.getCommandTags().contains("probe-installed"), "onInstall вызван");
        robot.setMode(RobotMode.WORK);
        context.runAtTick(6, () -> {
            context.assertTrue(robot.getInventory().count(Items.STICK) == 1, "Подключена рабочая цель");
            ItemStack removed = robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT);
            context.assertTrue(ModuleProgress.counter(removed) == 1, "Снятый предмет содержит прогресс");
            owner.setStackInHand(Hand.MAIN_HAND, removed);
        });
        context.runAtTick(35, () -> {
            context.assertTrue(robot.getModules().getModule(TestContent.PROBE_ID) == null
                    && robot.getCommandTags().contains("probe-removed"), "Цель снята и вызван onRemove");
            context.assertTrue(robot.getInventory().count(Items.STICK) == 1, "Снятая профессия больше не действует");
            robot.interact(owner, Hand.MAIN_HAND);
        });
        context.runAtTick(42, () -> {
            ItemStack installed = robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT);
            context.assertTrue(ModuleProgress.counter(installed) == 2, "Повторная установка продолжает счётчик");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void levelTransitionChangesRealCadenceAndEnergy(TestContext context) {
        RobotEntity robot = robot(context);
        ItemStack module = new ItemStack(TestContent.PROBE);
        module.getOrCreateNbt().putInt(ModuleProgress.COUNTER_KEY, 99);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, module);
        robot.setMode(RobotMode.WORK);
        context.runAtTick(5, () -> {
            context.assertTrue(ModuleProgress.counter(module) == 100 && ModuleProgress.level(module) == 2,
                    "Сотое успешное действие повышает уровень");
            context.assertTrue(robot.getEnergy() == 1585, "Первое действие стоило 15 по прежнему уровню");
        });
        context.runAtTick(12, () -> context.assertTrue(ModuleProgress.counter(module) == 100,
                "Интервал нельзя обойти частыми тиками цели"));
        context.runAtTick(24, () -> {
            context.assertTrue(ModuleProgress.counter(module) == 101 && robot.getEnergy() == 1571,
                    "Следующее действие через 15 тиков, 15 × 90 процентов округлено до 14");
            robot.setMode(RobotMode.IDLE);
        });
        context.runAtTick(45, () -> {
            context.assertTrue(ModuleProgress.counter(module) == 101 && robot.getEnergy() == 1571,
                    "IDLE останавливает цель без траты энергии");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void failedActionRefundsAndThirdLevelWorks(TestContext context) {
        RobotEntity robot = robot(context);
        ItemStack stack = new ItemStack(TestContent.PROBE);
        stack.getOrCreateNbt().putInt(ModuleProgress.COUNTER_KEY, 500);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, stack);
        robot.getModules().synchronize();
        robot.setMode(RobotMode.WORK);
        AbstractModule module = (AbstractModule) robot.getModules().getModule(TestContent.PROBE_ID);
        context.assertFalse(module.performAction(robot, 25, () -> false), "Отказ действия сообщается вызывающему");
        context.assertTrue(robot.getEnergy() == 1600 && ModuleProgress.counter(stack) == 500,
                "Отказ не расходует энергию и не даёт опыта");
        context.assertTrue(module.performAction(robot, 25, () -> true) && robot.getEnergy() == 1580,
                "Третий уровень расходует 80 процентов");
        context.assertTrue(ModuleProgress.interval(stack) == 10 && ModuleProgress.counter(stack) == 501,
                "Третий уровень: интервал 10 и один прирост опыта");
        context.assertFalse(module.performAction(robot, 25, () -> true), "Повтор в тот же тик отклоняется");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nbtRestoreRecreatesGoalsAndOldInstanceCannotAct(TestContext context) {
        RobotEntity robot = robot(context);
        ItemStack module = new ItemStack(TestContent.PROBE);
        module.getOrCreateNbt().putInt(ModuleProgress.COUNTER_KEY, 124);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, module);
        robot.getModules().synchronize();
        AbstractModule old = (AbstractModule) robot.getModules().getModule(TestContent.PROBE_ID);
        NbtCompound saved = robot.writeNbt(new NbtCompound());
        robot.readNbt(saved);
        robot.setMode(RobotMode.WORK);
        context.assertTrue(robot.getModules().getModule(TestContent.PROBE_ID) != old,
                "Загрузка создаёт новый экземпляр поведения");
        context.assertFalse(old.performAction(robot, 20, () -> true), "Старый экземпляр не меняет новый предмет");
        context.runAtTick(6, () -> {
            context.assertTrue(ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT)) == 125,
                    "После NBT-загрузки цель восстановилась и продолжила прогресс");
            robot.discard();
            context.assertTrue(robot.getModules().getModule(TestContent.PROBE_ID) == null
                    && robot.getCommandTags().contains("probe-removed"), "Удаление сущности освобождает цели");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void duplicateNbtNeverRunsTwoCopies(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(TestContent.PROBE));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(TestContent.PROBE));
        robot.setMode(RobotMode.WORK);
        context.runAtTick(6, () -> {
            context.assertTrue(robot.getInventory().count(Items.STICK) == 1,
                    "Даже ошибочный NBT не запускает профессию дважды");
            context.assertTrue(ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT + 1)) == 0,
                    "Неактивный дубликат остаётся предметом без выполнения действий");
            context.complete();
        });
    }

    private static RobotEntity robot(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        robot.setNoGravity(true);
        robot.setEnergy(1600);
        return robot;
    }
}
