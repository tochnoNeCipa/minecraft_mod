package com.robohelper.gametest;

import com.robohelper.ai.MiningSafety;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import com.robohelper.screen.RobotStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Тоннели в настоящем мире: оси, безопасность, энергия, сохранение и логист. */
public final class MinerTests implements FabricGameTest {
    private static final String TEMPLATE = "robohelper_gametest:forest";
    private static final String BATCH = "miner";
    private static final BlockPos HOME = new BlockPos(24, 2, 24);

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 400)
    public void northTunnel(TestContext context) { tunnel(context, Direction.NORTH); }
    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 400)
    public void eastTunnel(TestContext context) { tunnel(context, Direction.EAST); }
    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 400)
    public void southTunnel(TestContext context) { tunnel(context, Direction.SOUTH); }
    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 400)
    public void westTunnel(TestContext context) { tunnel(context, Direction.WEST); }

    private static void tunnel(TestContext context, Direction direction) {
        RobotEntity robot = robot(context, direction);
        corridor(context, direction, 3);
        context.addInstantFinalTask(() -> {
            context.assertTrue(counter(robot) == 6 && robot.getInventory().count(Items.COBBLESTONE) == 6,
                    "Шесть блоков тоннеля добыты в хранение: " + diagnostic(robot));
            for (int i = 1; i <= 3; i++) {
                BlockPos feet = HOME.offset(direction, i);
                context.assertTrue(context.getBlockState(feet).isAir() && context.getBlockState(feet.up()).isAir(), "Сечение 1×2");
                context.assertTrue(context.getBlockState(feet.up(2)).isOf(Blocks.STONE), "Крыша не затронута");
            }
            context.assertTrue(context.getBlockState(HOME.offset(direction, 4)).isOf(Blocks.BEDROCK), "Препятствие сохранено");
            context.assertTrue(robot.getMinerDirection() == direction && robot.getEnergy() <= 1450, "Направление установки и цена 25");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void containerStopsWholeSection(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        BlockPos cell = HOME.north();
        context.setBlockState(cell, Blocks.CHEST);
        context.setBlockState(cell.up(), Blocks.STONE);
        Inventory chest = (Inventory) context.getBlockEntity(cell);
        chest.setStack(0, new ItemStack(Items.DIAMOND, 7));
        context.runAtTick(100, () -> {
            context.assertTrue(context.getBlockState(cell).isOf(Blocks.CHEST) && chest.count(Items.DIAMOND) == 7,
                    "Контейнер и содержимое сохранены");
            context.assertTrue(context.getBlockState(cell.up()).isOf(Blocks.STONE) && counter(robot) == 0
                    && robot.getEnergy() == 1600, "Ни один блок сечения не сломан и энергия не потрачена");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void lavaAboveStopsBeforeBreaking(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        corridor(context, Direction.NORTH, 1);
        context.setBlockState(HOME.north().up(2), Blocks.LAVA);
        context.runAtTick(70, () -> {
            context.assertTrue(context.getBlockState(HOME.north()).isOf(Blocks.STONE)
                    && context.getBlockState(HOME.north().up()).isOf(Blocks.STONE)
                    && counter(robot) == 0 && robot.getEnergy() == 1600, "Лава над головой обнаружена до рубки");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void sideWaterStopsBeforeBreaking(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        corridor(context, Direction.NORTH, 1);
        context.setBlockState(HOME.north().east(), Blocks.WATER);
        context.runAtTick(60, () -> {
            context.assertTrue(counter(robot) == 0 && context.getBlockState(HOME.north()).isOf(Blocks.STONE),
                    "Жидкость сбоку не вскрыта");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void deepDropStopsMiner(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        corridor(context, Direction.NORTH, 1);
        for (int depth = 1; depth <= 4; depth++) {
            context.setBlockState(HOME.north().down(depth), Blocks.AIR);
        }
        context.runAtTick(70, () -> {
            context.assertTrue(counter(robot) == 0 && robot.getEnergy() == 1600
                    && context.getBlockState(HOME.north().up()).isOf(Blocks.STONE), "Обрыв проверен до разрушения");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH)
    public void blacklistAndBlockEntitiesAreProtected(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        for (var block : new net.minecraft.block.Block[]{Blocks.BEDROCK, Blocks.OBSIDIAN, Blocks.SPAWNER,
                Blocks.BARREL, Blocks.HOPPER, Blocks.FURNACE, Blocks.SHULKER_BOX}) {
            context.setBlockState(HOME.north(), block);
            context.assertTrue(MiningSafety.inspect(robot, context.getAbsolutePos(HOME.north())) == MiningSafety.Result.PROTECTED,
                    "Неразрушаемый блок, чёрный список и любой контейнер защищены");
        }
        context.complete();
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void lowEnergyResumesSameTunnel(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        corridor(context, Direction.NORTH, 3);
        robot.setEnergy(321);
        context.runAtTick(35, () -> {
            context.assertTrue(counter(robot) == 1 && robot.getEnergy() == 296, "При пороге остановка после одного блока");
            robot.setEnergy(1600);
        });
        context.addInstantFinalTask(() -> context.assertTrue(counter(robot) == 6
                && robot.getInventory().count(Items.COBBLESTONE) == 6, "После зарядки исходный тоннель продолжен"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void removingModuleStopsMining(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        corridor(context, Direction.NORTH, 3);
        ItemStack[] removed = new ItemStack[1];
        context.runAtTick(12, () -> {
            removed[0] = robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT);
            context.assertTrue(ModuleProgress.counter(removed[0]) == 1, "Первый блок добыт");
        });
        context.runAtTick(90, () -> {
            context.assertTrue(context.getBlockState(HOME.north()).isOf(Blocks.STONE)
                    && ModuleProgress.counter(removed[0]) == 1, "После снятия новых действий нет");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void savedRobotResumesOriginalPlan(TestContext context) {
        RobotEntity[] current = {robot(context, Direction.WEST)};
        corridor(context, Direction.WEST, 3);
        context.runAtTick(12, () -> {
            context.assertTrue(counter(current[0]) == 1, "Сохраняем частично выкопанное сечение");
            NbtCompound nbt = new NbtCompound();
            current[0].writeNbt(nbt);
            current[0].discard();
            current[0] = context.spawnEntity(ModEntities.ROBOT, HOME);
            current[0].readNbt(nbt);
        });
        context.addInstantFinalTask(() -> context.assertTrue(counter(current[0]) == 6
                && current[0].getInventory().count(Items.COBBLESTONE) == 6
                && current[0].getMinerDirection() == Direction.WEST, "NBT сохраняет ось, задание, шаг и добычу"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void logisticsUnloadsAndMinerResumes(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        corridor(context, Direction.NORTH, 3);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.LOGISTICS_MODULE));
        context.setBlockState(HOME.east(2), Blocks.BARREL);
        Inventory barrel = (Inventory) context.getBlockEntity(HOME.east(2));
        for (int i = 0; i < 13; i++) robot.getInventory().setStack(i, new ItemStack(Items.DIRT));
        context.addInstantFinalTask(() -> context.assertTrue(barrel.count(Items.DIRT) == 13
                && counter(robot) == 6 && robot.getInventory().count(Items.COBBLESTONE) + barrel.count(Items.COBBLESTONE) == 6,
                "Выгрузка прервала и возобновила тот же тоннель без потери добычи"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 1100)
    public void zoneBoundaryCompletesTunnel(TestContext context) {
        RobotEntity robot = robot(context, Direction.NORTH);
        robot.setEnergy(6400);
        corridor(context, Direction.NORTH, 16);
        context.addInstantFinalTask(() -> {
            context.assertTrue(counter(robot) == 30 && robot.getStatus() == RobotStatus.TUNNEL_COMPLETE, "15 сечений до границы, затем ожидание: " + diagnostic(robot));
            context.assertTrue(context.getBlockState(HOME.north(16)).isOf(Blocks.STONE)
                    && context.getBlockState(HOME.north(16).up()).isOf(Blocks.STONE), "За зоной блоки целы");
        });
    }

    private static RobotEntity robot(TestContext context, Direction direction) {
        for (int x = 8; x <= 41; x++) for (int z = 8; z <= 40; z++) {
            context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        }
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, HOME);
        robot.setYaw(direction.asRotation());
        robot.setZoneCenter(robot.getBlockPos());
        robot.setEnergy(1600);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.MINER_MODULE));
        robot.setMode(RobotMode.WORK);
        return robot;
    }

    private static void corridor(TestContext context, Direction direction, int length) {
        for (int i = 1; i <= length; i++) for (int y = 0; y <= 2; y++) {
            context.setBlockState(HOME.offset(direction, i).up(y), Blocks.STONE);
        }
        context.setBlockState(HOME.offset(direction, length + 1), Blocks.BEDROCK);
    }

    private static String diagnostic(RobotEntity robot) {
        BlockPos origin = robot.getZoneCenter();
        return "actions=" + counter(robot) + ", pos=" + robot.getPos().subtract(origin.getX(), origin.getY(), origin.getZ())
                + ", energy=" + robot.getEnergy() + ", state=" + robot.getStatus() + ", task="
                + robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT).getNbt();
    }

    private static int counter(RobotEntity robot) {
        return ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT));
    }
}
