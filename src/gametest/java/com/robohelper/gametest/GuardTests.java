package com.robohelper.gametest;

import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.module.ModuleProgress;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.Inventory;
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
import net.minecraft.util.math.Vec3d;

/** Настоящие удары, фильтры целей, паузы и прерывание производственных профессий. */
public final class GuardTests implements FabricGameTest {
    private static final String TEMPLATE = "robohelper_gametest:forest";
    private static final String BATCH = "guard";
    private static final BlockPos HOME = new BlockPos(24, 2, 24);
    private static long previousTime;

    @BeforeBatch(batchId = BATCH)
    public static void night(ServerWorld world) {
        previousTime = world.getTimeOfDay();
        world.setTimeOfDay(13000);
    }

    @AfterBatch(batchId = BATCH)
    public static void restore(ServerWorld world) { world.setTimeOfDay(previousTime); }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 500)
    public void defeatsZombieAndReturnsToCenter(TestContext context) {
        RobotEntity robot = robot(context, HOME.east(4));
        ZombieEntity zombie = zombie(context, HOME.east(6));
        context.addInstantFinalTask(() -> context.assertTrue(!zombie.isAlive()
                && robot.isNearZoneCenter() && robot.getTarget() == null && count(robot) == 5,
                "Пять ударов убили зомби; робот вернулся в центр и очистил цель"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void usesAttackAttributeAndPaysOnlyForHits(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        robot.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(6);
        ZombieEntity zombie = zombie(context, HOME.east());
        zombie.setHealth(12);
        context.addInstantFinalTask(() -> context.assertTrue(!zombie.isAlive() && count(robot) == 2
                && robot.getEnergy() == 1570, "Два удара по 6 с ценой 15, без фиктивного прогресса"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void maxLevelUsesTenTickIntervalAndDiscount(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT).getOrCreateNbt().putInt(ModuleProgress.COUNTER_KEY, 500);
        ZombieEntity zombie = zombie(context, HOME.east());
        context.runAtTick(65, () -> {
            context.assertTrue(!zombie.isAlive() && count(robot) == 505 && robot.getEnergy() == 1540,
                    "Уровень 3: пять ударов за 40 тиков от первого, по 12 энергии");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void ignoresPlayersAnimalsAndNeutralMobs(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        var cow = context.spawnMob(EntityType.COW, HOME.east());
        var wolf = context.spawnMob(EntityType.WOLF, HOME.west());
        var enderman = context.spawnMob(EntityType.ENDERMAN, HOME.north(2));
        var piglin = context.spawnMob(EntityType.ZOMBIFIED_PIGLIN, HOME.south(2));
        cow.setAiDisabled(true); wolf.setAiDisabled(true); enderman.setAiDisabled(true); piglin.setAiDisabled(true);
        PlayerEntity player = context.createMockSurvivalPlayer();
        Vec3d pos = robot.getPos().add(1, 0, 1);
        player.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        context.getWorld().spawnEntity(player);
        context.runAtTick(100, () -> {
            context.assertTrue(cow.getHealth() == cow.getMaxHealth() && wolf.getHealth() == wolf.getMaxHealth()
                    && enderman.getHealth() == enderman.getMaxHealth() && piglin.getHealth() == piglin.getMaxHealth()
                    && player.getHealth() == 20 && count(robot) == 0 && robot.getEnergy() == 1600,
                    "Игроки, животные и нейтральные HostileEntity не атакованы");
            player.discard();
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void ignoresOutsideZoneAndBeyondTenBlocks(TestContext context) {
        RobotEntity robot = robot(context, HOME.east(10));
        ZombieEntity outside = zombie(context, HOME.east(16));
        ZombieEntity distant = zombie(context, HOME.west());
        context.runAtTick(100, () -> {
            context.assertTrue(outside.getHealth() == 20 && distant.getHealth() == 20
                    && count(robot) == 0 && robot.getEnergy() == 1600, "Проверяются и радиус поиска, и рабочая зона");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 200)
    public void targetLeavingZoneIsReleased(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        ZombieEntity zombie = zombie(context, HOME.east());
        float[] health = {0};
        context.runAtTick(12, () -> {
            context.assertTrue(count(robot) == 1, "Первый удар нанесён");
            health[0] = zombie.getHealth();
            BlockPos outside = context.getAbsolutePos(HOME.east(16));
            zombie.setPosition(outside.getX() + 0.5, outside.getY(), outside.getZ() + 0.5);
        });
        context.runAtTick(100, () -> {
            context.assertTrue(robot.getTarget() == null && count(robot) == 1 && zombie.getHealth() == health[0]
                    && robot.isInsideWorkZone(robot.getBlockPos()), "Охранник не преследует и не бьёт за зоной");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void cannotAttackThroughWall(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        ZombieEntity zombie = zombie(context, HOME.east(2));
        for (int y = 0; y <= 2; y++) for (int z = -2; z <= 2; z++) {
            context.setBlockState(HOME.east().add(0, y, z), Blocks.STONE);
        }
        context.runAtTick(100, () -> {
            context.assertTrue(zombie.getHealth() == 20 && count(robot) == 0 && robot.getEnergy() == 1600,
                    "Нет ударов и оплаты через стену");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 240)
    public void lowEnergyPausesAndRechargeResumes(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        robot.setEnergy(321);
        ZombieEntity zombie = zombie(context, HOME.east());
        context.runAtTick(35, () -> {
            context.assertTrue(count(robot) == 1 && zombie.getHealth() == 16 && robot.getEnergy() == 306,
                    "После одного удара охрана остановилась на пороге энергии");
            robot.setEnergy(1600);
        });
        context.addInstantFinalTask(() -> context.assertTrue(!zombie.isAlive() && count(robot) == 5,
                "После дозарядки цель снова найдена и бой завершён"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void removalImmediatelyStopsAttacks(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        ZombieEntity zombie = zombie(context, HOME.east());
        ItemStack[] removed = new ItemStack[1];
        context.runAtTick(12, () -> {
            removed[0] = robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT);
            context.assertTrue(ModuleProgress.counter(removed[0]) == 1, "Один удар до снятия");
        });
        context.runAtTick(100, () -> {
            context.assertTrue(zombie.getHealth() == 16 && robot.getTarget() == null
                    && ModuleProgress.counter(removed[0]) == 1 && robot.getEnergy() == 1585,
                    "После снятия охраны нет цели, ударов и списаний");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void idleAndFollowDoNotAttack(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        ZombieEntity zombie = zombie(context, HOME.east());
        robot.setMode(RobotMode.IDLE);
        context.runAtTick(45, () -> robot.setMode(RobotMode.FOLLOW));
        context.runAtTick(100, () -> {
            context.assertTrue(zombie.getHealth() == 20 && count(robot) == 0 && robot.getEnergy() == 1600,
                    "Охрана отключена в IDLE и FOLLOW");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void noGuardModuleMeansNoAttack(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        robot.getInventory().removeStack(RobotInventory.FIRST_MODULE_SLOT);
        ZombieEntity zombie = zombie(context, HOME.east());
        context.runAtTick(100, () -> {
            context.assertTrue(zombie.getHealth() == 20 && robot.getTarget() == null && robot.getEnergy() == 1600,
                    "Сам робот без профессии не атакует");
            context.complete();
        });
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 160)
    public void fullStorageDoesNotDisableGuard(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        for (int i = 0; i < 18; i++) robot.getInventory().setStack(i, new ItemStack(Items.DIRT, 64));
        robot.collectWorkDrops(java.util.List.of(new ItemStack(Items.COBBLESTONE)));
        ZombieEntity zombie = zombie(context, HOME.east());
        zombie.setHealth(4);
        context.addInstantFinalTask(() -> context.assertTrue(!zombie.isAlive() && count(robot) == 1
                && robot.isStorageBlocked(), "Блокировка производства не блокирует охрану"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 650)
    public void guardInterruptsMinerThenMiningResumes(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.MINER_MODULE));
        robot.setYaw(180);
        robot.setEnergy(6400);
        for (int i = 1; i <= 3; i++) for (int y = 0; y <= 1; y++) context.setBlockState(HOME.north(i).up(y), Blocks.STONE);
        context.setBlockState(HOME.north(4), Blocks.BEDROCK);
        ZombieEntity[] enemy = new ZombieEntity[1];
        int[] miningAtCombat = {-1};
        context.runAtTick(12, () -> {
            enemy[0] = zombie(context, HOME.east());
            enemy[0].setHealth(8);
        });
        context.runAtEveryTick(() -> {
            if (enemy[0] != null && enemy[0].isAlive() && robot.getTarget() == enemy[0]) {
                int current = ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT + 1));
                if (miningAtCombat[0] < 0) miningAtCombat[0] = current;
                context.assertTrue(current == miningAtCombat[0], "Во время боя шахтёр приостановлен");
            }
        });
        context.addInstantFinalTask(() -> context.assertTrue(enemy[0] != null && !enemy[0].isAlive()
                && miningAtCombat[0] >= 0 && count(robot) == 2
                && ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT + 1)) == 6,
                "Охрана прервала шахтёра, вернулась в центр; исходный тоннель завершён"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 600)
    public void guardInterruptsLogisticsThenUnloadResumes(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.LOGISTICS_MODULE));
        context.setBlockState(HOME.west(2), Blocks.BARREL);
        Inventory barrel = (Inventory) context.getBlockEntity(HOME.west(2));
        for (int i = 0; i < 13; i++) robot.getInventory().setStack(i, new ItemStack(Items.DIRT));
        ZombieEntity[] enemy = new ZombieEntity[1];
        int[] unloadedAtCombat = {-1};
        context.runAtTick(12, () -> { enemy[0] = zombie(context, HOME.east()); enemy[0].setHealth(8); });
        context.runAtEveryTick(() -> {
            if (enemy[0] != null && enemy[0].isAlive() && robot.getTarget() == enemy[0]) {
                int unloaded = barrel.count(Items.DIRT);
                if (unloadedAtCombat[0] < 0) unloadedAtCombat[0] = unloaded;
                context.assertTrue(unloaded == unloadedAtCombat[0], "Во время боя логист приостановлен");
            }
        });
        context.addInstantFinalTask(() -> context.assertTrue(enemy[0] != null && !enemy[0].isAlive()
                && unloadedAtCombat[0] >= 0 && barrel.count(Items.DIRT) == 13 && count(robot) == 2,
                "После боя логист завершил поездку ниже порога заполнения"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH, tickLimit = 650)
    public void unreachableMovingTargetReleasesLogistics(TestContext context) {
        RobotEntity robot = robot(context, HOME);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT + 1, new ItemStack(ModItems.LOGISTICS_MODULE));
        context.setBlockState(HOME.west(2), Blocks.BARREL);
        Inventory barrel = (Inventory) context.getBlockEntity(HOME.west(2));
        for (int i = 0; i < 13; i++) robot.getInventory().setStack(i, new ItemStack(Items.DIRT));
        ZombieEntity zombie = zombie(context, HOME.east());
        context.runAtTick(12, () -> {
            context.assertTrue(count(robot) == 1, "Бой уже начался");
            BlockPos cell = HOME.east(5);
            for (int y = 0; y <= 2; y++) {
                context.setBlockState(cell.east().up(y), Blocks.STONE);
                context.setBlockState(cell.west().up(y), Blocks.STONE);
                context.setBlockState(cell.north().up(y), Blocks.STONE);
                context.setBlockState(cell.south().up(y), Blocks.STONE);
            }
            context.setBlockState(cell.up(2), Blocks.STONE);
            BlockPos absolute = context.getAbsolutePos(cell);
            zombie.setPosition(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        });
        context.addInstantFinalTask(() -> context.assertTrue(barrel.count(Items.DIRT) == 13
                && zombie.isAlive() && zombie.getHealth() == 16 && count(robot) == 1 && robot.getTarget() == null,
                "Недоступная цель отпущена по таймауту; логист продолжил работу"));
    }

    @GameTest(templateName = TEMPLATE, batchId = BATCH)
    public void bothNewRecipesUseCorrectTools(TestContext context) {
        ScreenHandler handler = new ScreenHandler(null, 0) {
            @Override public ItemStack quickMove(PlayerEntity p, int slot) { return ItemStack.EMPTY; }
            @Override public boolean canUse(PlayerEntity p) { return true; }
        };
        for (String profession : new String[]{"miner", "guard"}) {
            CraftingInventory input = new CraftingInventory(handler, 3, 3);
            input.setStack(0, new ItemStack(profession.equals("miner") ? Items.IRON_PICKAXE : Items.IRON_SWORD));
            input.setStack(1, new ItemStack(Items.REDSTONE)); input.setStack(2, new ItemStack(Items.REDSTONE));
            input.setStack(3, new ItemStack(Items.IRON_INGOT)); input.setStack(4, new ItemStack(Items.IRON_INGOT));
            CraftingRecipe recipe = (CraftingRecipe) context.getWorld().getRecipeManager()
                    .get(new Identifier("robohelper", profession + "_module")).orElseThrow();
            context.assertTrue(recipe.matches(input, context.getWorld())
                    && recipe.craft(input, context.getWorld().getRegistryManager()).isOf(
                            profession.equals("miner") ? ModItems.MINER_MODULE : ModItems.GUARD_MODULE), "Верный инструмент создаёт новый модуль");
            input.setStack(0, new ItemStack(Items.IRON_HOE));
            context.assertFalse(recipe.matches(input, context.getWorld()), "Мотыга не заменяет кирку или меч");
        }
        context.complete();
    }

    private static RobotEntity robot(TestContext context, BlockPos position) {
        for (int x = 8; x <= 41; x++) for (int z = 8; z <= 40; z++) context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, position);
        robot.setZoneCenter(context.getAbsolutePos(HOME));
        robot.setEnergy(1600);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(ModItems.GUARD_MODULE));
        robot.setMode(RobotMode.WORK);
        return robot;
    }

    private static ZombieEntity zombie(TestContext context, BlockPos position) {
        ZombieEntity zombie = context.spawnMob(EntityType.ZOMBIE, position);
        zombie.setAiDisabled(true);
        zombie.setPersistent();
        zombie.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue(0);
        zombie.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE).setBaseValue(1);
        return zombie;
    }

    private static int count(RobotEntity robot) {
        return ModuleProgress.counter(robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT));
    }
}
