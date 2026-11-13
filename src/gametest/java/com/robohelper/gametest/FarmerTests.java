package com.robohelper.gametest;

import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
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
import net.minecraft.world.GameRules;

/** Сбор и пересадка на настоящих грядках, совместная работа и защита от вытаптывания. */
public final class FarmerTests implements FabricGameTest {
    private static final String TEMPLATE = "robohelper_gametest:forest";
    private static final String BATCH = "farmer";
    private static final BlockPos HOME = new BlockPos(24, 2, 24);
    private static final BlockPos CROP = HOME.east(2);
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

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 400)
    public void allFourCropsHarvestAndReplant(TestContext context) {
        RobotEntity robot = robot(context);
        BlockPos[] cells = {CROP, HOME.west(2), HOME.south(2), HOME.north(2)};
        Block[] crops = {Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS};
        for (int i = 0; i < cells.length; i++) {
            crop(context, cells[i], crops[i], true);
        }
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        robot.getInventory().setStack(1, new ItemStack(Items.CARROT, 2));
        robot.getInventory().setStack(2, new ItemStack(Items.POTATO, 2));
        robot.getInventory().setStack(3, new ItemStack(Items.BEETROOT_SEEDS, 2));
        context.addInstantFinalTask(() -> {
            for (int i = 0; i < cells.length; i++) {
                context.assertTrue(context.getBlockState(cells[i]).isOf(crops[i])
                        && ((CropBlock) crops[i]).getAge(context.getBlockState(cells[i])) == 0,
                        "Каждая культура пересажена с нулевым возрастом");
                context.assertTrue(context.getBlockState(cells[i].down()).isOf(Blocks.FARMLAND), "Грядка сохранена");
            }
            context.assertTrue(counter(robot) == 8 && robot.getEnergy() == 1500,
                    "Четыре сбора и четыре посадки, 100 энергии без ходьбы");
            context.assertTrue(robot.getInventory().count(Items.WHEAT) >= 1
                    && robot.getInventory().count(Items.BEETROOT) >= 1, "Урожай собран в хранение");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void immatureCropsRemainUntouched(TestContext context) {
        RobotEntity robot = robot(context);
        crop(context, CROP, Blocks.WHEAT, false);
        context.runAtTick(100, () -> {
            context.assertTrue(context.getBlockState(CROP).isOf(Blocks.WHEAT)
                    && ((CropBlock) Blocks.WHEAT).getAge(context.getBlockState(CROP)) == 0, "Неспелая культура не тронута");
            context.assertTrue(counter(robot) == 0 && robot.getEnergy() == 1600, "Нет оплаты за поиск без действий");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void noSeedBeforeHarvestLeavesEmptyCell(TestContext context) {
        RobotEntity robot = robot(context);
        crop(context, CROP, Blocks.WHEAT, true);
        context.runAtTick(100, () -> {
            context.assertTrue(context.getBlockState(CROP).isAir(), "Без запаса семян клетка осталась пустой");
            context.assertTrue(robot.getInventory().count(Items.WHEAT) == 1 && counter(robot) == 1
                    && robot.getEnergy() == 1585, "Оплачен только один сбор, без фиктивной посадки");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void navigationCrossesFieldToCentralCrop(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        for (int x = 25; x <= 35; x++) {
            for (int z = 19; z <= 29; z++) {
                crop(context, new BlockPos(x, 2, z), Blocks.WHEAT, false);
            }
        }
        BlockPos ripe = new BlockPos(30, 2, 24);
        crop(context, ripe, Blocks.WHEAT, true);
        boolean[] crossedFarmland = {false};
        context.runAtEveryTick(() -> {
            BlockPos below = BlockPos.ofFloored(robot.getX(), robot.getY() - 0.01, robot.getZ());
            if (context.getWorld().getBlockState(below).isOf(Blocks.FARMLAND)) {
                crossedFarmland[0] = true;
            }
        });
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(ripe).isOf(Blocks.WHEAT)
                    && ((CropBlock) Blocks.WHEAT).getAge(context.getBlockState(ripe)) == 0,
                    "Урожай в середине сплошного поля собран и пересажен");
            context.assertTrue(crossedFarmland[0], "Робот действительно прошёл по грядкам");
            context.assertTrue(counter(robot) == 2 && robot.getInventory().count(Items.WHEAT) == 1,
                    "Собрана только одна спелая культура");
            for (int x = 25; x <= 35; x++) {
                for (int z = 19; z <= 29; z++) {
                    BlockPos cell = new BlockPos(x, 2, z);
                    context.assertTrue(context.getBlockState(cell.down()).isOf(Blocks.FARMLAND), "Все грядки сохранены");
                    context.assertTrue(context.getBlockState(cell).isOf(Blocks.WHEAT)
                            && ((CropBlock) Blocks.WHEAT).getAge(context.getBlockState(cell)) == 0,
                            "Неспелые посевы по пути не повреждены");
                }
            }
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void farmerStandingOnFarmlandCanWork(TestContext context) {
        RobotEntity robot = robot(context);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                context.setBlockState(HOME.add(dx, -1, dz), Blocks.FARMLAND);
            }
        }
        BlockPos ripe = HOME.east();
        crop(context, ripe, Blocks.WHEAT, true);
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        context.addInstantFinalTask(() -> {
            context.assertTrue(counter(robot) == 2 && context.getBlockState(ripe).isOf(Blocks.WHEAT)
                    && ((CropBlock) Blocks.WHEAT).getAge(context.getBlockState(ripe)) == 0,
                    "Стоя на грядке, фермер собирает и пересаживает соседний урожай");
            BlockPos below = BlockPos.ofFloored(robot.getX(), robot.getY() - 0.01, robot.getZ());
            context.assertTrue(context.getWorld().getBlockState(below).isOf(Blocks.FARMLAND),
                    "Действия выполнены с позиции на грядке");
            context.assertTrue(context.getBlockState(HOME.down()).isOf(Blocks.FARMLAND), "Грядка под роботом сохранена");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 120)
    public void fallingRobotDoesNotTrampleFarmland(TestContext context) {
        context.setBlockState(HOME.down(), Blocks.FARMLAND);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, HOME.up(6));
        context.runAtTick(60, () -> {
            context.assertTrue(robot.isOnGround(), "Робот приземлился");
            context.assertTrue(context.getBlockState(HOME.down()).isOf(Blocks.FARMLAND), "Падение не превращает грядку в землю");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void removalBetweenHarvestAndPlantStopsActions(TestContext context) {
        RobotEntity robot = robot(context);
        crop(context, CROP, Blocks.WHEAT, true);
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        ItemStack[] removed = new ItemStack[1];
        context.runAtTick(8, () -> {
            context.assertTrue(counter(robot) == 1 && context.getBlockState(CROP).isAir(), "Урожай собран до посадки");
            removed[0] = robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT);
        });
        context.runAtTick(80, () -> {
            context.assertTrue(context.getBlockState(CROP).isAir() && ModuleProgress.counter(removed[0]) == 1
                    && robot.getEnergy() == 1585, "Снятый модуль не продолжает посадку");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void playerReplacementIsNotOverwritten(TestContext context) {
        RobotEntity robot = robot(context);
        crop(context, CROP, Blocks.WHEAT, true);
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        int[] count = new int[1];
        context.runAtTick(8, () -> {
            context.assertTrue(counter(robot) == 1, "Сбор выполнен");
            count[0] = robot.getInventory().count(Items.WHEAT_SEEDS);
            context.setBlockState(CROP, Blocks.STONE);
        });
        context.runAtTick(80, () -> {
            context.assertTrue(context.getBlockState(CROP).isOf(Blocks.STONE), "Чужой новый блок не заменён растением");
            context.assertTrue(robot.getInventory().count(Items.WHEAT_SEEDS) == count[0]
                    && counter(robot) == 1 && robot.getEnergy() == 1585, "Несостоявшаяся посадка не оплачена");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void cropOutsideZoneIsIgnored(TestContext context) {
        RobotEntity robot = robot(context);
        crop(context, HOME.east(16), Blocks.CARROTS, true);
        context.runAtTick(100, () -> {
            context.assertTrue(((CropBlock) Blocks.CARROTS).isMature(context.getBlockState(HOME.east(16))),
                    "За радиусом урожай остаётся спелым");
            context.assertTrue(counter(robot) == 0 && robot.getEnergy() == 1600, "За зоной нет действий");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 240)
    public void idleLumberjackDoesNotStarveFarmerSearch(TestContext context) {
        RobotEntity robot = robot(context);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.LUMBERJACK_MODULE));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.FARMER_MODULE));
        robot.getInventory().setStack(0, new ItemStack(Items.WHEAT_SEEDS, 2));
        crop(context, CROP, Blocks.WHEAT, true);
        context.addInstantFinalTask(() -> {
            context.assertTrue(context.getBlockState(CROP).isOf(Blocks.WHEAT)
                    && ((CropBlock) Blocks.WHEAT).getAge(context.getBlockState(CROP)) == 0,
                    "Фермер получил очередь сканирования после лесоруба без деревьев");
            context.assertTrue(ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT + 1)) == 2,
                    "Оба действия фермера выполнены");
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH)
    public void farmerRecipeUsesHoe(TestContext context) {
        ScreenHandler handler = new ScreenHandler(null, 0) {
            @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean canUse(PlayerEntity player) { return true; }
        };
        CraftingInventory input = new CraftingInventory(handler, 3, 3);
        input.setStack(0, new ItemStack(Items.IRON_HOE));
        input.setStack(1, new ItemStack(Items.REDSTONE));
        input.setStack(2, new ItemStack(Items.REDSTONE));
        input.setStack(3, new ItemStack(Items.IRON_INGOT));
        input.setStack(4, new ItemStack(Items.IRON_INGOT));
        CraftingRecipe recipe = (CraftingRecipe) context.getWorld().getRecipeManager()
                .get(new Identifier("robohelper", "farmer_module")).orElseThrow();
        context.assertTrue(recipe.matches(input, context.getWorld())
                && recipe.craft(input, context.getWorld().getRegistryManager()).isOf(ModItems.FARMER_MODULE), "Рецепт создаёт модуль фермера");
        input.setStack(0, new ItemStack(Items.IRON_AXE));
        context.assertFalse(recipe.matches(input, context.getWorld()), "Топор не заменяет мотыгу");
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
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.FARMER_MODULE));
        robot.setMode(RobotMode.WORK);
        return robot;
    }

    private static void crop(TestContext context, BlockPos pos, Block block, boolean mature) {
        context.setBlockState(pos.down(), Blocks.FARMLAND);
        CropBlock crop = (CropBlock) block;
        context.setBlockState(pos, crop.withAge(mature ? crop.getMaxAge() : 0));
    }

    private static int counter(RobotEntity robot) {
        return ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT));
    }
}
