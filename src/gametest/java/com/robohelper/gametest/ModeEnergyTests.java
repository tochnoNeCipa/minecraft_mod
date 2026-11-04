package com.robohelper.gametest;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.registry.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Проверки энергии и реального движения по миру, включая восстановление старых сохранений. */
public final class ModeEnergyTests implements FabricGameTest {
    private static final String NAVIGATION = "robohelper_gametest:navigation";
    private static final BlockPos START = new BlockPos(2, 2, 7);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fuelCapacityAndExactFit(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        robot.setNoGravity(true);
        robot.setEnergy(4800);
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        context.runAtTick(3, () -> {
            context.assertTrue(robot.getEnergy() == 6400, "Уголь даёт 1600 при точном свободном месте");
            context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 1,
                    "Запасной уголь не сгорает при полном заряде");
            robot.setEnergy(4801);
        });
        context.runAtTick(6, () -> {
            context.assertTrue(robot.getEnergy() == 4801, "При свободных 1599 уголь не расходуется");
            robot.setEnergy(4800);
        });
        context.runAtTick(9, () -> {
            context.assertTrue(robot.getEnergy() == 6400, "После освобождения 1600 зарядка возобновляется");
            context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).isEmpty(),
                    "Израсходован ровно один оставшийся уголь");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oversizedFuelNeverWastesEnergy(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        robot.setNoGravity(true);
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL_BLOCK));
        context.runAtTick(4, () -> {
            context.assertTrue(robot.getEnergy() == 0, "Угольный блок не помещается в ёмкость 6400");
            context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 1,
                    "Слишком ёмкое топливо остаётся целым");
            robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.STONE));
        });
        context.runAtTick(8, () -> {
            context.assertTrue(robot.getEnergy() == 0, "Нет энергии из неподходящего предмета");
            context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).isOf(Items.STONE),
                    "Ошибочный предмет из NBT не уничтожается");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onlyOwnerCanInsertFuel(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        PlayerEntity owner = context.createMockSurvivalPlayer();
        PlayerEntity other = context.createMockSurvivalPlayer();
        robot.setOwnerUuid(owner.getUuid());
        other.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COAL, 2));
        robot.interact(other, Hand.MAIN_HAND);
        context.assertTrue(robot.getInventory().isEmpty(), "Чужой игрок не меняет инвентарь");
        context.assertTrue(other.getMainHandStack().getCount() == 2, "Чужое топливо не теряется");
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COAL, 3));
        robot.interact(owner, Hand.MAIN_HAND);
        context.assertTrue(owner.getMainHandStack().isEmpty(), "Топливо из руки перенесено");
        context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 3,
                "Все три угля находятся в слоте");
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.CHARCOAL, 2));
        robot.interact(owner, Hand.MAIN_HAND);
        context.assertTrue(owner.getMainHandStack().getCount() == 2, "Несовместимые стопки не смешиваются");
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL, 64));
        owner.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.COAL, 2));
        robot.interact(owner, Hand.MAIN_HAND);
        context.assertTrue(owner.getMainHandStack().getCount() == 2, "Полный слот не забирает предметы");
        owner.getAbilities().creativeMode = true;
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, ItemStack.EMPTY);
        robot.interact(owner, Hand.MAIN_HAND);
        context.assertTrue(owner.getMainHandStack().getCount() == 2, "В творческом режиме топливо не тратится");
        context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 2,
                "Топливо добавляется в творческом режиме");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void modeCycleAndEnergyBoundary(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        context.assertTrue(robot.getMode() == RobotMode.IDLE, "Новый робот ожидает");
        context.assertTrue(RobotMode.WORK.next() == RobotMode.IDLE
                && RobotMode.IDLE.next() == RobotMode.FOLLOW && RobotMode.FOLLOW.next() == RobotMode.WORK,
                "Порядок переключения режимов");
        robot.setEnergy(320);
        robot.setMode(RobotMode.WORK);
        context.assertFalse(robot.canWork(), "При ровно 5 процентах работа запрещена");
        robot.setEnergy(321);
        context.assertTrue(robot.canWork(), "Выше 5 процентов работа разрешена");
        context.assertFalse(robot.tryConsumeEnergy(322), "Перерасход запрещён");
        context.assertTrue(robot.getEnergy() == 321, "Отказ не меняет заряд");
        context.assertTrue(robot.tryConsumeEnergy(25) && robot.getEnergy() == 296, "Успешное списание атомарно");
        context.assertFalse(robot.canWork(), "После пересечения порога работа прекращается");
        robot.setEnergy(Integer.MAX_VALUE);
        context.assertTrue(robot.getEnergy() == RobotConfig.ENERGY_CAPACITY, "Верхняя граница заряда");
        robot.setEnergy(-100);
        context.assertTrue(robot.getEnergy() == 0, "Нижняя граница заряда");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void statePersistsAndOldSavesRemainValid(TestContext context) {
        RobotEntity original = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        BlockPos center = context.getAbsolutePos(new BlockPos(5, 3, 5));
        original.setZoneCenter(center);
        original.setEnergy(1234);
        original.setMode(RobotMode.FOLLOW);
        NbtCompound saved = original.writeNbt(new NbtCompound());
        RobotEntity loaded = ModEntities.ROBOT.create(context.getWorld());
        loaded.readNbt(saved);
        context.assertTrue(loaded.getEnergy() == 1234 && loaded.getMode() == RobotMode.FOLLOW,
                "Заряд и режим сохраняются");
        context.assertTrue(loaded.getZoneCenter().equals(center) && loaded.getZoneRadius() == 15,
                "Центр и радиус сохраняются");
        saved.putString("Mode", "INVALID");
        saved.putInt("Energy", -1);
        saved.putIntArray("ZoneCenter", new int[]{1, 2});
        saved.putInt("ZoneRadius", 1000);
        loaded.readNbt(saved);
        context.assertTrue(loaded.getMode() == RobotMode.IDLE && loaded.getEnergy() == 0,
                "Некорректные данные не запускают работу");
        context.assertTrue(loaded.getZoneCenter().equals(loaded.getBlockPos()) && loaded.getZoneRadius() == 15,
                "Некорректная зона безопасно ограничивается");
        for (String key : new String[]{"Energy", "Mode", "ZoneCenter", "ZoneRadius"}) {
            saved.remove(key);
        }
        loaded.readNbt(saved);
        context.assertTrue(loaded.getMode() == RobotMode.IDLE && loaded.getEnergy() == 0
                && loaded.getZoneCenter().equals(loaded.getBlockPos()) && loaded.getZoneRadius() == 15,
                "Сохранения части 2 загружаются с безопасными значениями по умолчанию");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void zoneIncludesOnlyAllowedCoordinates(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        BlockPos center = robot.getBlockPos();
        robot.setZoneCenter(center);
        context.assertTrue(robot.isInsideWorkZone(center.add(15, 8, 0)), "Границы радиуса и высоты включены");
        context.assertTrue(robot.isInsideWorkZone(center.add(0, -8, -15)), "Отрицательные границы включены");
        context.assertFalse(robot.isInsideWorkZone(center.add(15, 0, 1)), "Диагональ вне круга запрещена");
        context.assertFalse(robot.isInsideWorkZone(center.add(0, 9, 0)), "Верхняя граница высоты");
        context.assertFalse(robot.isInsideWorkZone(center.add(0, -9, 0)), "Нижняя граница высоты");
        context.assertFalse(robot.isInsideWorkZone(center.add(16, 0, 0)), "Горизонтальная граница");
        robot.setZoneCenter(center.add(20, 0, 0));
        context.assertFalse(robot.isInsideWorkZone(center), "Перенос центра изменяет область");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 120)
    public void idleAndEmptyWorkDoNotSpendEnergy(TestContext context) {
        context.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 2, 2, 2);
        robot.setZoneCenter(robot.getBlockPos());
        robot.setEnergy(1600);
        Vec3d start = robot.getPos();
        robot.setMode(RobotMode.WORK);
        context.runAtTick(40, () -> {
            context.assertTrue(robot.getPos().squaredDistanceTo(start) < 0.01, "Без модулей робот не гуляет");
            context.assertTrue(robot.getEnergy() == 1600, "Ожидание в WORK бесплатно");
            robot.setMode(RobotMode.IDLE);
        });
        context.runAtTick(80, () -> {
            context.assertTrue(robot.getPos().squaredDistanceTo(start) < 0.01, "В IDLE робот стоит");
            context.assertTrue(robot.getEnergy() == 1600, "IDLE бесплатен");
            context.complete();
        });
    }

    @GameTest(templateName = NAVIGATION, tickLimit = 300)
    public void workReturnsAllTheWayToCenter(TestContext context) {
        floor(context);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 25, 2, 7);
        robot.setZoneCenter(context.getAbsolutePos(START));
        robot.setEnergy(1600);
        robot.setMode(RobotMode.WORK);
        context.assertFalse(robot.canStartModuleWork(), "Нельзя работать вне зоны");
        context.addInstantFinalTask(() -> {
            context.assertTrue(robot.isNearZoneCenter() && !robot.requiresZoneReturn(),
                    "Нужно вернуться к центру, а не только пересечь границу");
            context.assertTrue(robot.getEnergy() < 1600 && robot.getEnergy() >= 1580,
                    "Ходьба расходует энергию без чрезмерного списания");
            context.assertTrue(robot.canStartModuleWork(), "После возвращения можно работать");
        });
    }

    @GameTest(templateName = NAVIGATION, tickLimit = 150)
    public void lowEnergyStopsReturnNavigation(TestContext context) {
        floor(context);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, 25, 2, 7);
        robot.setZoneCenter(context.getAbsolutePos(START));
        robot.setMode(RobotMode.WORK);
        robot.setEnergy(320);
        Vec3d initial = robot.getPos();
        Vec3d[] stopped = new Vec3d[1];
        context.runAtTick(30, () -> {
            context.assertTrue(robot.getPos().squaredDistanceTo(initial) < 0.01, "На пороге робот не идёт");
            robot.setEnergy(321);
        });
        context.runAtTick(90, () -> {
            context.assertTrue(robot.getPos().squaredDistanceTo(initial) > 1, "При 321 движение началось");
            context.assertTrue(robot.getEnergy() == 320, "20 тиков ходьбы списывают 1 энергию");
            context.assertTrue(robot.getNavigation().isIdle(), "На пороге навигация останавливается");
            stopped[0] = robot.getPos();
        });
        context.runAtTick(120, () -> {
            context.assertTrue(robot.getPos().squaredDistanceTo(stopped[0]) < 0.01,
                    "При низком заряде робот не продолжает путь");
            context.complete();
        });
    }

    @GameTest(templateName = NAVIGATION, tickLimit = 220)
    public void followWalksForFreeAndKeepsDistance(TestContext context) {
        floor(context);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, START);
        ServerPlayerEntity owner = owner(context, new BlockPos(7, 2, 7));
        robot.setOwnerUuid(owner.getUuid());
        robot.setMode(RobotMode.FOLLOW);
        Vec3d initial = robot.getPos();
        context.runAtTick(15, () -> {
            context.assertTrue(robot.getPos().squaredDistanceTo(initial) < 0.01,
                    "В пределах 6 блоков следование не запускается");
            position(context, owner, new BlockPos(15, 2, 7));
        });
        context.runAtTick(120, () -> {
            context.assertTrue(robot.squaredDistanceTo(owner) >= 4 && robot.squaredDistanceTo(owner) <= 36,
                    "Робот подошёл к владельцу, сохранив дистанцию");
            context.assertTrue(robot.getEnergy() == 0, "Следование доступно без топлива");
            removeOwner(context, owner);
            context.complete();
        });
    }

    @GameTest(templateName = NAVIGATION, tickLimit = 300)
    public void teleportThenWorkReturnsToOriginalZone(TestContext context) {
        floor(context);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, START);
        robot.setZoneCenter(robot.getBlockPos());
        BlockPos initialCenter = robot.getZoneCenter();
        ServerPlayerEntity owner = owner(context, new BlockPos(30, 2, 7));
        robot.setOwnerUuid(owner.getUuid());
        robot.setMode(RobotMode.FOLLOW);
        context.runAtTick(12, () -> {
            context.assertTrue(robot.squaredDistanceTo(owner) <= 36, "Дальше 24 блоков робот телепортируется");
            context.assertTrue(robot.getEnergy() == 0, "Телепортация бесплатна");
            context.assertTrue(robot.getZoneCenter().equals(initialCenter), "FOLLOW не переносит рабочую зону");
            robot.setEnergy(1600);
            robot.setMode(RobotMode.WORK);
        });
        context.addInstantFinalTask(() -> {
            context.assertTrue(robot.getMode() == RobotMode.WORK && robot.isNearZoneCenter()
                    && !robot.requiresZoneReturn(), "После FOLLOW робот вернулся в исходную зону");
            removeOwner(context, owner);
        });
    }

    @GameTest(templateName = NAVIGATION, tickLimit = 100)
    public void teleportAvoidsHazardsAndRetries(TestContext context) {
        floor(context);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, START);
        BlockPos ownerPos = new BlockPos(38, 2, 7);
        ServerPlayerEntity owner = owner(context, ownerPos);
        for (int x = 36; x <= 40; x++) {
            for (int z = 5; z <= 9; z++) {
                context.setBlockState(new BlockPos(x, 1, z), Blocks.MAGMA_BLOCK);
            }
        }
        robot.setOwnerUuid(owner.getUuid());
        robot.setMode(RobotMode.FOLLOW);
        context.runAtTick(15, () -> {
            context.assertTrue(robot.squaredDistanceTo(owner) > 24 * 24,
                    "Робот не телепортируется на опасную опору");
            for (int x = 36; x <= 40; x++) {
                for (int z = 5; z <= 9; z++) {
                    context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
                }
            }
        });
        context.runAtTick(40, () -> {
            context.assertTrue(robot.squaredDistanceTo(owner) <= 36,
                    "После появления безопасной опоры телепортация повторяется");
            context.assertTrue(robot.getEnergy() == 0, "Повторные попытки не тратят энергию");
            removeOwner(context, owner);
            context.complete();
        });
    }

    @GameTest(templateName = NAVIGATION, tickLimit = 100)
    public void idleStopsFollowingAndAbsentOwnerIsSafe(TestContext context) {
        floor(context);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, START);
        ServerPlayerEntity owner = owner(context, new BlockPos(15, 2, 7));
        robot.setOwnerUuid(owner.getUuid());
        robot.setMode(RobotMode.FOLLOW);
        RobotEntity orphan = context.spawnEntity(ModEntities.ROBOT, 3, 2, 12);
        orphan.setOwnerUuid(UUID.randomUUID());
        orphan.setMode(RobotMode.FOLLOW);
        Vec3d orphanPos = orphan.getPos();
        Vec3d[] stopped = new Vec3d[1];
        context.runAtTick(12, () -> {
            robot.setMode(RobotMode.IDLE);
            context.assertTrue(robot.getNavigation().isIdle(), "Смена режима сразу останавливает путь");
        });
        context.runAtTick(30, () -> {
            stopped[0] = robot.getPos();
            position(context, owner, new BlockPos(38, 2, 7));
        });
        context.runAtTick(60, () -> {
            context.assertTrue(robot.getPos().squaredDistanceTo(stopped[0]) < 0.01,
                    "В IDLE робот не следует и не телепортируется");
            context.assertTrue(orphan.getPos().squaredDistanceTo(orphanPos) < 0.01,
                    "Без владельца в мире робот остаётся на месте");
            removeOwner(context, owner);
            context.complete();
        });
    }

    private static void floor(TestContext context) {
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 16; z++) {
                context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    private static ServerPlayerEntity owner(TestContext context, BlockPos pos) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        position(context, player, pos);
        return player;
    }

    private static void position(TestContext context, PlayerEntity player, BlockPos pos) {
        BlockPos absolute = context.getAbsolutePos(pos);
        player.refreshPositionAndAngles(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
    }

    private static void removeOwner(TestContext context, ServerPlayerEntity owner) {
        context.getWorld().getServer().getPlayerManager().remove(owner);
    }
}
