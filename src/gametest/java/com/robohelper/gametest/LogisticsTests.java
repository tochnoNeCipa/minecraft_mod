package com.robohelper.gametest;

import com.robohelper.ai.ContainerFinder;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import com.robohelper.screen.RobotStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.BeforeBatch;
import net.minecraft.test.AfterBatch;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import java.util.List;

/** Реальные контейнеры, квоты и совместные производственные циклы без подмены переноса. */
public final class LogisticsTests implements FabricGameTest {
    private static final String TEMPLATE = "robohelper_gametest:forest";
    private static final String BATCH = "logistics";
    private static final BlockPos HOME = new BlockPos(24, 2, 24);
    private static final BlockPos BOX = HOME.east(2);
    private static int randomTicks;

    @BeforeBatch(batchId = BATCH)
    public static void prepare(ServerWorld world) {
        randomTicks = world.getGameRules().getInt(GameRules.RANDOM_TICK_SPEED);
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(0, world.getServer());
    }

    @AfterBatch(batchId = BATCH)
    public static void restore(ServerWorld world) {
        world.getGameRules().get(GameRules.RANDOM_TICK_SPEED).set(randomTicks, world.getServer());
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void thresholdAndWholeTrip(TestContext context) {
        RobotEntity robot = robot(context);
        Inventory chest = box(context, BOX, Blocks.CHEST);
        cargo(robot, 12, Items.STONE, 1);
        context.runAtTick(30, () -> {
            context.assertTrue(chest.isEmpty() && count(robot) == 0 && robot.getEnergy() == 1600,
                    "12/18 меньше 70 процентов, поездка не начинается");
            robot.getInventory().setStack(12, new ItemStack(Items.STONE));
        });
        context.addInstantFinalTask(() -> {
            context.assertTrue(chest.count(Items.STONE) == 13 && robot.getInventory().count(Items.STONE) == 0,
                    "13/18 запускает выгрузку до конца, даже после снижения заполнения");
            context.assertTrue(count(robot) == 13 && robot.getEnergy() == 1535,
                    "13 стопок: 13 действий по 5 энергии");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void plantingQuotaIsPerKindAndFuelSlotStays(TestContext context) {
        RobotEntity robot = robot(context);
        Inventory chest = box(context, BOX, Blocks.CHEST);
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 64));
        robot.getInventory().setStack(1, new ItemStack(Items.WHEAT_SEEDS, 64));
        robot.getInventory().setStack(2, new ItemStack(Items.CARROT, 40));
        robot.getInventory().setStack(3, new ItemStack(Items.CARROT, 40));
        robot.getInventory().setStack(4, new ItemStack(Items.OAK_SAPLING, 32));
        ItemStack marked = new ItemStack(Items.OAK_SAPLING, 64);
        marked.getOrCreateNbt().putInt("Marker", 9);
        robot.getInventory().setStack(5, marked);
        robot.getInventory().setStack(6, new ItemStack(Items.OAK_LOG, 64));
        for (int slot = 7; slot < 13; slot++) {
            robot.getInventory().setStack(slot, new ItemStack(Items.STONE));
        }
        robot.setEnergy(6400);
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL, 7));
        context.addInstantFinalTask(() -> {
            context.assertTrue(chest.count(Items.WHEAT_SEEDS) == 64 && chest.count(Items.CARROT) == 16
                    && chest.count(Items.OAK_SAPLING) == 32 && chest.count(Items.OAK_LOG) == 64
                    && chest.count(Items.STONE) == 6, "Излишки и брёвна выгружены");
            context.assertTrue(robot.getInventory().count(Items.WHEAT_SEEDS) == 64
                    && robot.getInventory().count(Items.CARROT) == 64 && robot.getInventory().count(Items.OAK_SAPLING) == 64,
                    "Сохранено ровно до 64 каждого вида, а не каждой стопки");
            context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 7,
                    "Топливный слот сохранён при полном заряде");
            context.assertTrue(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT).isOf(ModItems.LOGISTICS_MODULE),
                    "Установленный модуль не является грузом");
            boolean tag = false;
            for (int slot = 0; slot < chest.size(); slot++) {
                ItemStack stack = chest.getStack(slot);
                if (stack.isOf(Items.OAK_SAPLING) && stack.hasNbt() && stack.getNbt().getInt("Marker") == 9) {
                    tag = true;
                }
            }
            context.assertTrue(tag, "Перенос сохраняет произвольный NBT предметов");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 550)
    public void fullNearestChestIsSkippedForBarrel(TestContext context) {
        RobotEntity robot = robot(context);
        cargo(robot, 13, Items.STONE, 1);
        Inventory full = box(context, BOX, Blocks.CHEST);
        fill(full, Items.COBBLESTONE);
        Inventory barrel = box(context, HOME.east(8), Blocks.BARREL);
        context.addInstantFinalTask(() -> {
            context.assertTrue(barrel.count(Items.STONE) == 13 && robot.getInventory().count(Items.STONE) == 0,
                    "После полного ближайшего сундука выбрана доступная бочка");
            context.assertTrue(full.count(Items.COBBLESTONE) == 27 * 64, "Предметы сундука не извлекаются и не заменяются");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void doubleChestUsesBothHalves(TestContext context) {
        RobotEntity robot = robot(context);
        doubleChest(context, BOX);
        Inventory left = (Inventory) context.getBlockEntity(BOX);
        Inventory right = (Inventory) context.getBlockEntity(BOX.east());
        fill(left, Items.COBBLESTONE);
        Inventory combined = ContainerFinder.resolve(robot, context.getAbsolutePos(BOX));
        context.assertTrue(combined != null && combined.size() == 54, "Сундук объединён в 54 слота");
        cargo(robot, 13, Items.STONE, 1);
        context.addInstantFinalTask(() -> {
            context.assertTrue(right.count(Items.STONE) == 13 && left.count(Items.COBBLESTONE) == 27 * 64,
                    "Свободная вторая половина используется без изменения полной первой");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 180)
    public void doubleChestAcrossZoneBoundaryIsIgnored(TestContext context) {
        RobotEntity robot = robot(context);
        BlockPos edge = HOME.east(15);
        doubleChest(context, edge);
        cargo(robot, 13, Items.STONE, 1);
        context.assertTrue(ContainerFinder.resolve(robot, context.getAbsolutePos(edge)) == null,
                "Нельзя использовать половину двойного сундука за зоной");
        context.runAtTick(100, () -> {
            context.assertTrue(((Inventory) context.getBlockEntity(edge)).isEmpty()
                    && ((Inventory) context.getBlockEntity(edge.east())).isEmpty(), "Обе половины не затронуты");
            context.assertTrue(robot.getInventory().count(Items.STONE) == 13 && count(robot) == 0,
                    "Груз не потерян без подходящего контейнера");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void withoutLogisticsNoAutomaticTransfer(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT);
        cargo(robot, 13, Items.STONE, 1);
        Inventory chest = box(context, BOX, Blocks.CHEST);
        context.runAtTick(100, () -> {
            context.assertTrue(chest.isEmpty() && robot.getInventory().count(Items.STONE) == 13,
                    "Без модуля нужна ручная выгрузка");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 550)
    public void fullContainerWaitsWithoutCostAndRecovers(TestContext context) {
        RobotEntity robot = robot(context);
        cargo(robot, 13, Items.STONE, 1);
        Inventory chest = box(context, BOX, Blocks.CHEST);
        fill(chest, Items.COBBLESTONE);
        context.runAtTick(100, () -> {
            context.assertTrue(count(robot) == 0 && robot.getEnergy() == 1600
                    && robot.getInventory().count(Items.STONE) == 13, "Ожидание не расходует груз и энергию");
            context.assertTrue(robot.getStatus() == RobotStatus.NO_CONTAINER, "GUI сообщает об отсутствии места");
            chest.removeStack(0);
        });
        context.addInstantFinalTask(() -> {
            context.assertTrue(chest.count(Items.STONE) == 13 && count(robot) == 13, "После освобождения места работа возобновляется");
            context.assertTrue(drops(context, robot, Items.STONE) == 0, "Ничего не выбрасывается при неудачном поиске");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void removalWhileWalkingStopsTransfer(TestContext context) {
        RobotEntity robot = robot(context);
        cargo(robot, 13, Items.STONE, 1);
        Inventory chest = box(context, HOME.east(10), Blocks.CHEST);
        ItemStack[] removed = new ItemStack[1];
        context.runAtTick(8, () -> removed[0] = robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT));
        context.runAtTick(100, () -> {
            context.assertTrue(chest.isEmpty() && robot.getInventory().count(Items.STONE) == 13
                    && ModuleProgress.counter(removed[0]) == 0, "Снятый логист не переносит предметы позже");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void lowEnergyPausesAndResumesTrip(TestContext context) {
        RobotEntity robot = robot(context);
        robot.setEnergy(321);
        cargo(robot, 13, Items.STONE, 1);
        Inventory chest = box(context, BOX, Blocks.CHEST);
        context.runAtTick(30, () -> {
            context.assertTrue(chest.count(Items.STONE) == 1 && count(robot) == 1 && robot.getEnergy() == 316,
                    "На пороге выполнен только один оплаченный перенос");
        });
        context.runAtTick(60, () -> robot.setEnergy(1600));
        context.addInstantFinalTask(() -> {
            context.assertTrue(chest.count(Items.STONE) == 13 && robot.getInventory().count(Items.STONE) == 0,
                    "После зарядки продолжается незаконченная поездка ниже 70 процентов");
            context.assertTrue(count(robot) == 13 && robot.getEnergy() == 1540, "Оплачены оставшиеся 12 переносов");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 700)
    public void lumberjackAndLogisticsCompleteProductionCycle(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.LUMBERJACK_MODULE));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.LOGISTICS_MODULE));
        robot.getInventory().setStack(0, new ItemStack(Items.OAK_SAPLING, 2));
        for (int slot = 1; slot <= 11; slot++) {
            robot.getInventory().setStack(slot, new ItemStack(Items.STONE));
        }
        BlockPos tree = HOME.east(2);
        tree(context, tree);
        Inventory chest = box(context, HOME.west(2), Blocks.CHEST);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(tree).isOf(Blocks.OAK_SAPLING), "После поездки лесоруб завершил посадку");
            context.assertTrue(chest.count(Items.STONE) == 11 && chest.count(Items.OAK_LOG) > 0,
                    "Порог после добычи запустил автоматическую выгрузку");
            context.assertTrue(chest.count(Items.OAK_LOG) + robot.getInventory().count(Items.OAK_LOG) == 3,
                    "Все три бревна сохранены");
            context.assertTrue(ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT)) == 4,
                    "Прерывание логистом не сбросило задание лесоруба");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 850)
    public void blockedProducerResumesAfterLogisticsFreesStorage(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.LUMBERJACK_MODULE));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.LOGISTICS_MODULE));
        robot.getInventory().setStack(0, new ItemStack(Items.OAK_SAPLING));
        for (int slot = 1; slot < 18; slot++) {
            robot.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        }
        robot.collectWorkDrops(List.of(new ItemStack(Items.OAK_LOG)));
        context.assertTrue(robot.isStorageBlocked(), "Общее получение добычи зафиксировало переполнение");
        tree(context, HOME.east(2));
        Inventory chest = box(context, HOME.west(2), Blocks.CHEST);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(HOME.east(2)).isOf(Blocks.OAK_SAPLING), "После выгрузки производство продолжилось");
            context.assertTrue(chest.count(Items.STONE) == 17 * 64 && !robot.isStorageBlocked(), "Логист работал при заблокированном производстве");
            context.assertTrue(robot.getInventory().count(Items.OAK_LOG) + chest.count(Items.OAK_LOG)
                    + drops(context, robot, Items.OAK_LOG) == 4, "Добыча до и после переполнения не потеряна");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 850)
    public void farmerAndLogisticsKeepSeedsAndResumeHarvest(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.FARMER_MODULE));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.LOGISTICS_MODULE));
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        for (int slot = 1; slot < 18; slot++) {
            robot.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        }
        robot.collectWorkDrops(List.of(new ItemStack(Items.WHEAT)));
        BlockPos crop = HOME.east(2);
        context.setBlockState(crop.down(), Blocks.FARMLAND);
        context.setBlockState(crop, ((CropBlock) Blocks.WHEAT).withAge(7));
        Inventory chest = box(context, HOME.west(2), Blocks.BARREL);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(crop).isOf(Blocks.WHEAT)
                    && ((CropBlock) Blocks.WHEAT).getAge(context.getBlockState(crop)) == 0, "Фермер возобновился и пересадил культуру");
            context.assertTrue(chest.count(Items.STONE) == 17 * 64 && chest.count(Items.WHEAT_SEEDS) == 0,
                    "Запас семян сохранился при полной выгрузке");
            context.assertTrue(robot.getInventory().count(Items.WHEAT_SEEDS) >= 1
                    && robot.getInventory().count(Items.WHEAT) + chest.count(Items.WHEAT)
                    + drops(context, robot, Items.WHEAT) == 2, "Урожай и прежнее переполнение сохранены");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void outsideContainerAndFurnaceAreIgnored(TestContext context) {
        RobotEntity robot = robot(context);
        cargo(robot, 13, Items.STONE, 1);
        Inventory outside = box(context, HOME.east(16), Blocks.BARREL);
        Inventory furnace = box(context, BOX, Blocks.FURNACE);
        context.runAtTick(100, () -> {
            context.assertTrue(outside.isEmpty() && furnace.isEmpty() && robot.getInventory().count(Items.STONE) == 13,
                    "За зоной и в неподдерживаемый блок перенос не выполняется");
            context.assertTrue(count(robot) == 0 && robot.getEnergy() == 1600, "Нет фиктивных переносов");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 700)
    public void partialStackThenNextContainerPreservesCounts(TestContext context) {
        RobotEntity robot = robot(context);
        cargo(robot, 13, Items.DIRT, 1);
        robot.getInventory().setStack(0, new ItemStack(Items.STONE, 10));
        Inventory chest = box(context, BOX, Blocks.CHEST);
        fill(chest, Items.COBBLESTONE);
        chest.setStack(0, new ItemStack(Items.STONE, 60));
        Inventory barrel = box(context, HOME.east(8), Blocks.BARREL);
        context.addInstantFinalTask(() -> {
            context.assertTrue(chest.count(Items.STONE) == 64 && barrel.count(Items.STONE) == 6
                    && barrel.count(Items.DIRT) == 12, "Четыре предмета вошли в сундук, остаток дошёл до бочки");
            context.assertTrue(robot.getInventory().count(Items.STONE) == 0 && robot.getInventory().count(Items.DIRT) == 0
                    && count(robot) == 14, "Частичный перенос не дублирует и не теряет остаток");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void plantingSuppliesAloneDoNotCauseEmptyTrips(TestContext context) {
        RobotEntity robot = robot(context);
        Item[] supplies = {Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING, Items.JUNGLE_SAPLING,
                Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING, Items.CHERRY_SAPLING, Items.MANGROVE_PROPAGULE,
                Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.CARROT, Items.POTATO, Items.PUMPKIN_SEEDS};
        for (int slot = 0; slot < supplies.length; slot++) {
            robot.getInventory().setStack(slot, new ItemStack(supplies[slot]));
        }
        Inventory chest = box(context, BOX, Blocks.CHEST);
        context.runAtTick(100, () -> {
            context.assertTrue(chest.isEmpty() && count(robot) == 0 && robot.getEnergy() == 1600,
                    "13 зарезервированных видов не запускают пустые поездки");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 350)
    public void idleLogisticsDoesNotCancelProducerNavigation(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.LUMBERJACK_MODULE));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.LOGISTICS_MODULE));
        robot.getInventory().setStack(0, new ItemStack(Items.OAK_SAPLING));
        BlockPos tree = HOME.east(6);
        tree(context, tree);
        box(context, HOME.west(2), Blocks.CHEST);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(tree).isOf(Blocks.OAK_SAPLING), "Ожидающий логист не останавливает чужой путь");
            context.assertTrue(robot.getInventory().count(Items.OAK_LOG) == 3
                    && ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT + 1)) == 0,
                    "Лесоруб работает сам до порога заполнения");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH)
    public void logisticsRecipeUsesHopper(TestContext context) {
        ScreenHandler handler = new ScreenHandler(null, 0) {
            @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean canUse(PlayerEntity player) { return true; }
        };
        CraftingInventory input = new CraftingInventory(handler, 3, 3);
        input.setStack(0, new ItemStack(Items.HOPPER));
        input.setStack(1, new ItemStack(Items.REDSTONE));
        input.setStack(2, new ItemStack(Items.REDSTONE));
        input.setStack(3, new ItemStack(Items.IRON_INGOT));
        input.setStack(4, new ItemStack(Items.IRON_INGOT));
        CraftingRecipe recipe = (CraftingRecipe) context.getWorld().getRecipeManager()
                .get(new Identifier("robohelper", "logistics_module")).orElseThrow();
        context.assertTrue(recipe.matches(input, context.getWorld())
                && recipe.craft(input, context.getWorld().getRegistryManager()).isOf(ModItems.LOGISTICS_MODULE), "Рецепт логиста загружен");
        input.setStack(0, new ItemStack(Items.CHEST));
        context.assertFalse(recipe.matches(input, context.getWorld()), "Сундук не заменяет воронку");
        context.complete();
    }

    private static RobotEntity robot(TestContext context) {
        for (int x = 8; x <= 41; x++) {
            for (int z = 8; z <= 40; z++) {
                context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, HOME);
        robot.setZoneCenter(robot.getBlockPos());
        robot.setEnergy(1600);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.LOGISTICS_MODULE));
        robot.setMode(RobotMode.WORK);
        return robot;
    }

    private static Inventory box(TestContext context, BlockPos pos, Block block) {
        context.setBlockState(pos, block);
        return (Inventory) context.getBlockEntity(pos);
    }

    private static void cargo(RobotEntity robot, int slots, Item item, int amount) {
        for (int slot = 0; slot < slots; slot++) {
            robot.getInventory().setStack(slot, new ItemStack(item, amount));
        }
    }

    private static void fill(Inventory inventory, Item item) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.setStack(slot, new ItemStack(item, 64));
        }
    }

    private static void doubleChest(TestContext context, BlockPos left) {
        context.setBlockState(left, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, ChestType.LEFT));
        context.setBlockState(left.east(), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, ChestType.RIGHT));
        context.setBlockState(left, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH)
                .with(ChestBlock.CHEST_TYPE, ChestType.LEFT));
    }

    private static void tree(TestContext context, BlockPos root) {
        context.setBlockState(root.down(), Blocks.DIRT);
        for (int y = 0; y < 3; y++) {
            context.setBlockState(root.up(y), Blocks.OAK_LOG);
        }
        context.setBlockState(root.add(1, 1, 0), Blocks.OAK_LEAVES.getDefaultState()
                .with(LeavesBlock.PERSISTENT, false).with(LeavesBlock.DISTANCE, 1));
        context.setBlockState(root.up(3), Blocks.OAK_LEAVES.getDefaultState()
                .with(LeavesBlock.PERSISTENT, false).with(LeavesBlock.DISTANCE, 1));
    }

    private static int count(RobotEntity robot) {
        return ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT));
    }

    private static int drops(TestContext context, RobotEntity robot, Item type) {
        return context.getWorld().getEntitiesByType(EntityType.ITEM, robot.getBoundingBox().expand(5),
                item -> item.getStack().isOf(type)).stream().mapToInt(item -> item.getStack().getCount()).sum();
    }
}
