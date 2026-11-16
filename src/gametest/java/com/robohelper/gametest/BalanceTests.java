package com.robohelper.gametest;

import com.google.gson.GsonBuilder;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import com.robohelper.screen.RobotStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Воспроизводимые игровые замеры: тики/энергия, а не скорость ускоренного GameTest-сервера. */
public final class BalanceTests implements FabricGameTest {
    private static final String TEMPLATE = "robohelper_gametest:forest";
    private static final String BATCH = "balance";
    private static final BlockPos HOME = new BlockPos(24, 2, 24);
    private static int previousRandomTicks;

    @BeforeBatch(batchId = BATCH)
    public static void prepare(ServerWorld world) {
        previousRandomTicks = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, world.getServer());
    }

    @AfterBatch(batchId = BATCH)
    public static void restore(ServerWorld world) {
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(previousRandomTicks, world.getServer());
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 6000)
    public void sixLogOakTenTrialsPerLevel(TestContext context) {
        floor(context);
        List<Map<String, Object>> results = new ArrayList<>();
        RobotEntity[] robot = {null};
        int[] trial = {0};
        long[] started = {0};
        long[] cutFinished = {0};
        context.runAtEveryTick(() -> {
            if (robot[0] == null) {
                int level = trial[0] / 10;
                BlockPos tree = HOME.east(2);
                context.setBlockState(tree.down(), Blocks.DIRT);
                for (int y = 0; y < 6; y++) context.setBlockState(tree.up(y), Blocks.OAK_LOG);
                context.setBlockState(tree.up(6), Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, false));
                robot[0] = robot(context, HOME, ModItems.LUMBERJACK_MODULE, level);
                robot[0].getInventory().setStack(0, new ItemStack(Items.OAK_SAPLING));
                started[0] = context.getWorld().getTime();
                cutFinished[0] = 0;
            }
            if (robot[0].getInventory().count(Items.OAK_LOG) == 6 && cutFinished[0] == 0) cutFinished[0] = context.getWorld().getTime();
            if (!context.getBlockState(HOME.east(2)).isOf(Blocks.OAK_SAPLING)) return;
            int level = trial[0] / 10;
            int used = 6400 - robot[0].getEnergy();
            context.assertTrue(used == new int[]{130, 117, 104}[level], "Цена шести брёвен и одной посадки соответствует уровню");
            context.assertTrue(counter(robot[0]) == baseline(level) + 7, "Семь успешных действий без дублей");
            results.add(Map.of("level", level + 1, "trial", trial[0] % 10 + 1,
                    "cutTicksFromJobStart", cutFinished[0] - started[0],
                    "cycleTicks", context.getWorld().getTime() - started[0], "cycleEnergy", used));
            robot[0].discard(); robot[0] = null;
            if (++trial[0] == 30) { write("oak.json", results); context.complete(); }
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 4000)
    public void fifteenBlockTunnelAtEveryLevel(TestContext context) {
        floor(context);
        List<Map<String, Object>> results = new ArrayList<>();
        RobotEntity[] robot = {null};
        int[] level = {0};
        long[] started = {0};
        context.runAtEveryTick(() -> {
            if (robot[0] == null) {
                for (int i = 1; i <= 16; i++) for (int y = 0; y <= 2; y++) context.setBlockState(HOME.north(i).up(y), Blocks.STONE);
                robot[0] = robot(context, HOME, ModItems.MINER_MODULE, level[0]);
                started[0] = context.getWorld().getTime();
            }
            if (robot[0].getStatus() != RobotStatus.TUNNEL_COMPLETE) return;
            context.assertTrue(counter(robot[0]) == baseline(level[0]) + 30
                    && robot[0].getInventory().count(Items.COBBLESTONE) == 30, "Тоннель 15×1×2 завершён");
            int actionEnergy = 30 * new int[]{25, 23, 20}[level[0]];
            int used = 6400 - robot[0].getEnergy();
            context.assertTrue(used >= actionEnergy && used - actionEnergy < 40, "Только действия и реальная ходьба");
            results.add(Map.of("level", level[0] + 1, "length", 15, "blocks", 30,
                    "ticks", context.getWorld().getTime() - started[0], "totalEnergy", used,
                    "actionEnergy", actionEnergy, "walkingEnergy", used - actionEnergy));
            robot[0].discard(); robot[0] = null;
            if (++level[0] == 3) { write("tunnel.json", results); context.complete(); }
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 7000)
    public void nineByNineFieldEnergyAndPreservation(TestContext context) {
        floor(context);
        int cells = 0;
        for (int x = 20; x <= 28; x++) for (int z = 20; z <= 28; z++) {
            BlockPos pos = new BlockPos(x, 2, z);
            if (x == 24 && z == 24) { context.setBlockState(pos.down(), Blocks.WATER); continue; }
            context.setBlockState(pos.down(), Blocks.FARMLAND);
            context.setBlockState(pos, ((CropBlock) Blocks.WHEAT).withAge(7)); cells++;
        }
        RobotEntity robot = robot(context, new BlockPos(19, 2, 24), ModItems.FARMER_MODULE, 0);
        robot.setZoneCenter(context.getAbsolutePos(HOME));
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 64));
        robot.getInventory().setStack(1, new ItemStack(Items.WHEAT_SEEDS, 16));
        long started = context.getWorld().getTime();
        int cropCount = cells;
        context.addInstantFinalTask(() -> {
            context.assertTrue(counter(robot) == 160 && robot.getInventory().count(Items.WHEAT) == 80,
                    "Все 80 культур собраны и пересажены");
            for (int x = 20; x <= 28; x++) for (int z = 20; z <= 28; z++) {
                if (x == 24 && z == 24) continue;
                BlockPos pos = new BlockPos(x, 2, z);
                context.assertTrue(context.getBlockState(pos.down()).isOf(Blocks.FARMLAND)
                        && context.getBlockState(pos).isOf(Blocks.WHEAT)
                        && ((CropBlock) Blocks.WHEAT).getAge(context.getBlockState(pos)) == 0, "Почва и пересадки сохранены");
            }
            int used = 6400 - robot.getEnergy();
            // Первые 100 действий уровня 1: 1250; следующие 60 уровня 2: 690.
            context.assertTrue(used >= 1940 && used - 1940 < 100, "Учтён переход на уровень 2 посреди поля");
            write("field.json", Map.of("size", "9x9", "waterCells", 1, "crops", cropCount,
                    "actions", 160, "ticks", context.getWorld().getTime() - started,
                    "startingLevel", 1, "endingLevel", ModuleProgress.level(robot.getInventory().getStack(19)),
                    "totalEnergy", used, "actionEnergy", 1940, "walkingEnergy", used - 1940));
        });
    }

    private static int baseline(int level) { return new int[]{0, 100, 500}[level]; }
    private static int counter(RobotEntity robot) { return ModuleProgress.counter(robot.getInventory().getStack(19)); }
    private static RobotEntity robot(TestContext context, BlockPos pos, Item module, int level) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, pos);
        robot.setYaw(180); robot.setZoneCenter(robot.getBlockPos()); robot.setEnergy(6400);
        robot.getInventory().setStack(19, new ItemStack(module));
        robot.getInventory().getStack(19).getOrCreateNbt().putInt(ModuleProgress.COUNTER_KEY, baseline(level));
        robot.setMode(RobotMode.WORK);
        return robot;
    }
    private static void floor(TestContext context) {
        for (int x = 8; x <= 41; x++) for (int z = 8; z <= 40; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
    }
    private static void write(String name, Object results) {
        try {
            Path dir = Path.of("../balance-results"); Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), new GsonBuilder().setPrettyPrinting().create().toJson(results));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
