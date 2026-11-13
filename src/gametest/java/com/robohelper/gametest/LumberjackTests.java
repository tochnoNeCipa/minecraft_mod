package com.robohelper.gametest;

import com.robohelper.ai.TreeFinder;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import com.robohelper.screen.RobotStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;

/** Ограничения и полный цикл лесоруба на изолированных площадках настоящего сервера. */
public final class LumberjackTests implements FabricGameTest {
    private static final String FOREST = "robohelper_gametest:forest";
    private static final String BATCH = "lumberjack";
    private static final BlockPos ROBOT = new BlockPos(24, 2, 24);
    private static final BlockPos TREE = ROBOT.east(2);
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

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 250)
    public void harvestsAndReplantsOak(TestContext context) {
        RobotEntity robot = robot(context, 1600, 2);
        tree(context, TREE, false);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(TREE).isOf(Blocks.OAK_SAPLING), "После рубки посажен саженец");
            context.assertTrue(robot.getInventory().count(Items.OAK_LOG) == 3, "Три бревна прямо в инвентаре");
            context.assertTrue(robot.getInventory().count(Items.OAK_SAPLING) == 1, "Потрачен один саженец");
            context.assertTrue(counter(robot) == 4 && robot.getEnergy() == 1530,
                    "Три рубки и посадка: четыре действия, 70 энергии");
            context.assertTrue(context.getBlockState(TREE.add(1, 1, 0)).isOf(Blocks.OAK_LEAVES),
                    "Лесоруб не ломает листву");
            context.assertTrue(logDrops(context, robot) == 0, "Добыча не выброшена на землю при свободном месте");
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 180)
    public void ignoresBareLogsAndPersistentLeaves(TestContext context) {
        RobotEntity robot = robot(context, 1600, 1);
        tree(context, TREE, true);
        BlockPos bare = TREE.east(4);
        context.setBlockState(bare.down(), Blocks.DIRT);
        context.setBlockState(bare, Blocks.OAK_LOG);
        context.runAtTick(100, () -> {
            context.assertTrue(context.getBlockState(TREE.up(2)).isOf(Blocks.OAK_LOG)
                    && context.getBlockState(bare).isOf(Blocks.OAK_LOG), "Постройки не затронуты");
            context.assertTrue(counter(robot) == 0 && robot.getEnergy() == 1600,
                    "Отвергнутые цели не расходуют энергию и не дают опыт");
            context.complete();
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 300)
    public void diagonalBfsCutsHighestFirst(TestContext context) {
        RobotEntity robot = robot(context, 1600, 1);
        tree(context, TREE, false);
        BlockPos high = TREE.add(2, 4, 2);
        context.setBlockState(TREE.add(1, 3, 1), Blocks.OAK_LOG);
        context.setBlockState(high, Blocks.OAK_LOG);
        context.setBlockState(high.up(), Blocks.OAK_LEAVES.getDefaultState()
                .with(LeavesBlock.PERSISTENT, false).with(LeavesBlock.DISTANCE, 1));
        context.runAtTick(8, () -> {
            context.assertTrue(context.getBlockState(high).isAir() && context.getBlockState(TREE).isOf(Blocks.OAK_LOG),
                    "Первым срублено верхнее диагональное бревно");
            context.assertTrue(counter(robot) == 1, "За один интервал ломается один блок");
        });
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(TREE).isOf(Blocks.OAK_SAPLING), "Диагональное дерево завершено");
            context.assertTrue(robot.getInventory().count(Items.OAK_LOG) == 5 && counter(robot) == 6,
                    "Все пять соединённых по диагонали брёвен собраны");
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 500)
    public void leavesOutsideZoneLogUntouched(TestContext context) {
        RobotEntity robot = robot(context, 1600, 1);
        BlockPos edge = ROBOT.east(15);
        tree(context, edge, false);
        // Естественная листва находится со стороны, входящей в зону.
        context.setBlockState(edge.add(-1, 1, 0), Blocks.OAK_LEAVES.getDefaultState()
                .with(LeavesBlock.PERSISTENT, false).with(LeavesBlock.DISTANCE, 1));
        BlockPos outside = edge.add(1, 2, 0);
        context.setBlockState(outside, Blocks.OAK_LOG);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(edge).isOf(Blocks.OAK_SAPLING), "Дерево на границе обработано: pos=" + robot.getBlockPos().subtract(context.getAbsolutePos(ROBOT)) + " actions=" + counter(robot)
                    + " status=" + robot.getStatus() + " idle=" + robot.getNavigation().isIdle());
            context.assertTrue(context.getBlockState(outside).isOf(Blocks.OAK_LOG), "За границей бревно не ломается");
            context.assertTrue(robot.getInventory().count(Items.OAK_LOG) == 3 && counter(robot) == 4,
                    "Добыты только три разрешённых бревна");
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 170)
    public void oversizedGroupRejectedWithoutPartialDestruction(TestContext context) {
        RobotEntity robot = robot(context, 1600, 1);
        context.setBlockState(TREE.down(), Blocks.DIRT);
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 6; z++) {
                    context.setBlockState(TREE.add(x, y, z), Blocks.OAK_LOG);
                }
            }
        }
        context.setBlockState(TREE.add(6, 3, 5), Blocks.OAK_LEAVES.getDefaultState()
                .with(LeavesBlock.PERSISTENT, false).with(LeavesBlock.DISTANCE, 1));
        context.assertTrue(TreeFinder.inspect(robot, context.getAbsolutePos(TREE)) == null,
                "BFS отклоняет группу больше 128 блоков");
        context.runAtTick(100, () -> {
            context.assertTrue(counter(robot) == 0 && robot.getEnergy() == 1600, "Нет частичной рубки большой группы");
            context.assertTrue(context.getBlockState(TREE.add(5, 3, 5)).isOf(Blocks.OAK_LOG), "Группа сохранена");
            context.complete();
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 300)
    public void lowEnergyPausesThenResumesSameTree(TestContext context) {
        RobotEntity robot = robot(context, 330, 2);
        tree(context, TREE, false);
        context.runAtTick(30, () -> {
            context.assertTrue(counter(robot) == 1 && robot.getEnergy() == 310, "После первой рубки достигнут порог");
            context.assertTrue(context.getBlockState(TREE).isOf(Blocks.OAK_LOG)
                    && context.getBlockState(TREE.up()).isOf(Blocks.OAK_LOG), "Остальные брёвна не тронуты: root=" + context.getBlockState(TREE) + " mid=" + context.getBlockState(TREE.up())
                    + " robotDelta=" + robot.getBlockPos().subtract(context.getAbsolutePos(ROBOT)));

            context.assertTrue(robot.getStatus() == RobotStatus.LOW_ENERGY, "GUI показывает нехватку энергии");
        });
        context.runAtTick(60, () -> robot.setEnergy(1600));
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(TREE).isOf(Blocks.OAK_SAPLING), "Работа возобновлена после зарядки");
            context.assertTrue(counter(robot) == 4 && robot.getEnergy() == 1550, "Оплачены только оставшиеся действия");
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 180)
    public void removingModuleStopsFurtherChopping(TestContext context) {
        RobotEntity robot = robot(context, 1600, 1);
        tree(context, TREE, false);
        ItemStack[] removed = new ItemStack[1];
        context.runAtTick(8, () -> {
            context.assertTrue(counter(robot) == 1, "Первая рубка выполнена");
            removed[0] = robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT);
        });
        context.runAtTick(100, () -> {
            context.assertTrue(context.getBlockState(TREE).isOf(Blocks.OAK_LOG)
                    && context.getBlockState(TREE.up()).isOf(Blocks.OAK_LOG), "Без модуля рубка прекращается");
            context.assertTrue(ModuleProgress.counter(removed[0]) == 1 && robot.getEnergy() == 1580,
                    "Опыт предмета сохранён, лишняя энергия не списана");
            context.complete();
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 300)
    public void fullStorageDropsOnceThenResumesAfterSpaceFreed(TestContext context) {
        RobotEntity robot = robot(context, 1600, 1);
        for (int slot = 1; slot < 18; slot++) {
            robot.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        }
        tree(context, TREE, false);
        context.runAtTick(30, () -> {
            context.assertTrue(counter(robot) == 1 && robot.isStorageBlocked(), "После переполнения работа остановлена");
            context.assertTrue(logDrops(context, robot) == 1, "Одно непоместившееся бревно выпало под роботом: drops=" + logDrops(context, robot)
                    + " delta=" + robot.getBlockPos().subtract(context.getAbsolutePos(ROBOT)));
            context.assertTrue(robot.getStatus() == RobotStatus.STORAGE_FULL, "GUI показывает заполнение");
            robot.getInventory().removeStack(1);
        });
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(TREE).isOf(Blocks.OAK_SAPLING), "Освобождение места возобновляет работу");
            context.assertTrue(counter(robot) == 4 && robot.getInventory().count(Items.OAK_LOG) == 2
                    && logDrops(context, robot) == 1, "Все три бревна сохранены без повторных выбросов");
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 220)
    public void noSaplingLeavesEmptyRootWithoutFakeAction(TestContext context) {
        RobotEntity robot = robot(context, 1600, 0);
        tree(context, TREE, false);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(TREE).isAir(), "Без саженца основание остаётся пустым");
            context.assertTrue(robot.getInventory().count(Items.OAK_LOG) == 3 && counter(robot) == 3
                    && robot.getEnergy() == 1540, "Нет опыта или расхода за несостоявшуюся посадку");
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 550)
    public void skipsUnreachableTreeAndKeepsFiveMinuteCache(TestContext context) {
        RobotEntity robot = robot(context, 1600, 2);
        BlockPos enclosed = ROBOT.east(4);
        BlockPos reachable = ROBOT.east(10);
        tree(context, enclosed, false);
        tree(context, reachable, false);
        wall(context, enclosed, true);
        context.runAtTick(260, () -> {
            context.assertTrue(context.getBlockState(reachable).isOf(Blocks.OAK_SAPLING),
                    "Робот выбрал доступное дерево вместо закрытого стеной: pos=" + robot.getBlockPos().subtract(context.getAbsolutePos(ROBOT))
                            + " actions=" + counter(robot) + " status=" + robot.getStatus() + " idle=" + robot.getNavigation().isIdle());
            context.assertTrue(context.getBlockState(enclosed).isOf(Blocks.OAK_LOG), "Недоступное дерево не затронуто");
            wall(context, enclosed, false);
        });
        context.runAtTick(450, () -> {
            context.assertTrue(context.getBlockState(enclosed).isOf(Blocks.OAK_LOG) && counter(robot) == 4,
                    "Снятие стены не обходит пятиминутный кэш недоступного дерева");
            context.complete();
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH, tickLimit = 180)
    public void movedZoneCancelsOldTreeJob(TestContext context) {
        RobotEntity robot = robot(context, 1600, 1);
        tree(context, TREE, false);
        context.runAtTick(8, () -> {
            context.assertTrue(counter(robot) == 1, "Рубка началась");
            robot.setZoneCenter(robot.getBlockPos().west(20));
        });
        context.runAtTick(100, () -> {
            context.assertTrue(counter(robot) == 1 && context.getBlockState(TREE).isOf(Blocks.OAK_LOG)
                    && context.getBlockState(TREE.up()).isOf(Blocks.OAK_LOG), "Старая цель за новой зоной больше не ломается");
            context.complete();
        });
    }

    @GameTest(templateName = FOREST, batchId = BATCH)
    public void recipeCreatesFreshModule(TestContext context) {
        ScreenHandler handler = new ScreenHandler(null, 0) {
            @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean canUse(PlayerEntity player) { return true; }
        };
        CraftingInventory input = new CraftingInventory(handler, 3, 3);
        input.setStack(0, new ItemStack(Items.IRON_AXE));
        input.setStack(2, new ItemStack(Items.REDSTONE));
        input.setStack(4, new ItemStack(Items.REDSTONE));
        input.setStack(6, new ItemStack(Items.IRON_INGOT));
        input.setStack(8, new ItemStack(Items.IRON_INGOT));
        CraftingRecipe recipe = (CraftingRecipe) context.getWorld().getRecipeManager()
                .get(new Identifier("robohelper", "lumberjack_module")).orElseThrow();
        context.assertTrue(recipe.matches(input, context.getWorld()), "Загружен бесформенный рецепт из ТЗ");
        ItemStack result = recipe.craft(input, context.getWorld().getRegistryManager());
        context.assertTrue(result.isOf(ModItems.LUMBERJACK_MODULE) && result.getCount() == 1
                && ModuleProgress.counter(result) == 0, "Крафт создаёт один новый модуль без чужого опыта");
        input.removeStack(8);
        context.assertFalse(recipe.matches(input, context.getWorld()), "Без одного слитка рецепт не работает");
        context.complete();
    }

    private static RobotEntity robot(TestContext context, int energy, int saplings) {
        for (int x = 8; x <= 41; x++) {
            for (int z = 8; z <= 40; z++) {
                context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, ROBOT);
        robot.setZoneCenter(robot.getBlockPos());
        robot.setEnergy(energy);
        if (saplings > 0) {
            robot.getInventory().setStack(0, new ItemStack(Items.OAK_SAPLING, saplings));
        }
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.LUMBERJACK_MODULE));
        robot.setMode(RobotMode.WORK);
        return robot;
    }

    private static void tree(TestContext context, BlockPos root, boolean persistentLeaves) {
        context.setBlockState(root.down(), Blocks.DIRT);
        for (int y = 0; y < 3; y++) {
            context.setBlockState(root.up(y), Blocks.OAK_LOG);
        }
        context.setBlockState(root.add(1, 1, 0), Blocks.OAK_LEAVES.getDefaultState()
                .with(LeavesBlock.PERSISTENT, persistentLeaves).with(LeavesBlock.DISTANCE, 1));
        context.setBlockState(root.up(3), Blocks.OAK_LEAVES.getDefaultState()
                .with(LeavesBlock.PERSISTENT, persistentLeaves).with(LeavesBlock.DISTANCE, 1));
    }

    private static void wall(TestContext context, BlockPos root, boolean present) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != 2) {
                    continue;
                }
                for (int y = 0; y < 3; y++) {
                    context.setBlockState(root.add(dx, y, dz), present ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    private static int counter(RobotEntity robot) {
        return ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT));
    }

    private static int logDrops(TestContext context, RobotEntity robot) {
        int count = 0;
        for (ItemEntity item : context.getWorld().getEntitiesByType(EntityType.ITEM,
                robot.getBoundingBox().expand(4), entity -> entity.getStack().isOf(Items.OAK_LOG))) {
            count += item.getStack().getCount();
        }
        return count;
    }
}
