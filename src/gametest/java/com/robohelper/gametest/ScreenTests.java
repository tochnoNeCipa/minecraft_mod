package com.robohelper.gametest;

import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.item.RobotModuleItem;
import com.robohelper.registry.ModEntities;
import com.robohelper.screen.RobotScreenHandler;
import com.robohelper.screen.RobotStatus;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Проверки настоящего контейнера через серверные действия, без подмены инвентаря. */
public final class ScreenTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownerOpensAndOtherPlayerCannot(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        robot.interact(owner, Hand.MAIN_HAND);
        context.assertTrue(owner.currentScreenHandler instanceof RobotScreenHandler, "Открыт экран робота");
        RobotScreenHandler menu = (RobotScreenHandler) owner.currentScreenHandler;
        context.assertTrue(menu.slots.size() == 58, "22 слота робота и 36 слотов игрока");
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));
        context.assertTrue(menu.getSlot(0).getStack().getCount() == 3, "Экран использует настоящий инвентарь");
        PlayerEntity other = context.createMockSurvivalPlayer();
        other.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), 0, 0);
        robot.interact(other, Hand.MAIN_HAND);
        context.assertFalse(other.currentScreenHandler instanceof RobotScreenHandler, "Чужой экран не открывается");
        context.assertTrue(robot.createMenu(99, other.getInventory(), other) == null,
                "Фабрика контейнера также проверяет владельца");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void buttonsCycleModeMoveZoneAndRejectUnknownIds(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        context.assertTrue(menu.onButtonClick(owner, RobotScreenHandler.MODE_BUTTON)
                && robot.getMode() == RobotMode.FOLLOW, "IDLE → FOLLOW");
        menu.onButtonClick(owner, RobotScreenHandler.MODE_BUTTON);
        context.assertTrue(robot.getMode() == RobotMode.WORK, "FOLLOW → WORK");
        menu.onButtonClick(owner, RobotScreenHandler.MODE_BUTTON);
        context.assertTrue(robot.getMode() == RobotMode.IDLE, "WORK → IDLE");
        robot.setZoneCenter(robot.getBlockPos().add(20, 0, 0));
        menu.onButtonClick(owner, RobotScreenHandler.ZONE_BUTTON);
        context.assertTrue(robot.getZoneCenter().equals(robot.getBlockPos()), "Центр перенесён к роботу");
        context.assertFalse(menu.onButtonClick(owner, 999), "Неизвестная кнопка отклоняется");
        context.assertTrue(robot.getMode() == RobotMode.IDLE, "Неизвестная кнопка не меняет режим");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void directionRequiresMinerAndSurvivesNbt(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        context.assertFalse(menu.hasMiner(), "Без модуля направление недоступно");
        context.assertFalse(menu.onButtonClick(owner, RobotScreenHandler.DIRECTION_BUTTON),
                "Поддельное нажатие без шахтёра отклоняется сервером");
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(TestContent.MINER));
        robot.setYaw(90);
        context.assertTrue(menu.hasMiner() && menu.onButtonClick(owner, RobotScreenHandler.DIRECTION_BUTTON),
                "С шахтёром направление меняется");
        context.assertTrue(robot.getMinerDirection() == Direction.WEST, "Берётся направление взгляда робота");
        RobotEntity loaded = ModEntities.ROBOT.create(context.getWorld());
        loaded.readNbt(robot.writeNbt(new NbtCompound()));
        context.assertTrue(loaded.getMinerDirection() == Direction.WEST, "Направление сохраняется в NBT");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unauthorizedAndDistantActionsDoNotChangeState(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 5));
        PlayerEntity other = context.createMockSurvivalPlayer();
        other.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), 0, 0);
        for (int button = 0; button <= 3; button++) {
            context.assertFalse(menu.onButtonClick(other, button), "Чужая кнопка отклоняется");
        }
        menu.onSlotClick(0, 0, SlotActionType.PICKUP, other);
        context.assertTrue(menu.quickMove(other, 0).isEmpty(), "Чужой Shift-перенос отклоняется");
        context.assertTrue(menu.getCursorStack().isEmpty() && robot.getInventory().getStack(0).getCount() == 5,
                "Чужие клики не меняют предметы");
        owner.refreshPositionAndAngles(robot.getX() + 30, robot.getY(), robot.getZ(), 0, 0);
        context.assertFalse(menu.canUse(owner), "Нет доступа издалека");
        context.assertFalse(menu.onButtonClick(owner, RobotScreenHandler.UNLOAD_BUTTON), "Нет удалённой выгрузки");
        menu.onSlotClick(0, 0, SlotActionType.PICKUP, owner);
        context.assertTrue(robot.getInventory().getStack(0).getCount() == 5, "Нет удалённого забора предметов");
        context.assertTrue(robot.getMode() == RobotMode.IDLE, "Режим не изменился");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fuelSlotRejectsStoneAndAcceptsCoal(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        menu.setCursorStack(new ItemStack(Items.STONE, 3));
        menu.onSlotClick(RobotInventory.FUEL_SLOT, 0, SlotActionType.PICKUP, owner);
        context.assertTrue(menu.getCursorStack().getCount() == 3
                && robot.getInventory().getStack(RobotInventory.FUEL_SLOT).isEmpty(), "Камень остаётся на курсоре");
        owner.getInventory().insertStack(menu.getCursorStack());
        menu.setCursorStack(new ItemStack(Items.COAL, 3));
        menu.onSlotClick(RobotInventory.FUEL_SLOT, 0, SlotActionType.PICKUP, owner);
        context.assertTrue(menu.getCursorStack().isEmpty()
                && robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 3, "Уголь принят");
        owner.getInventory().setStack(9, new ItemStack(Items.COAL, 2));
        menu.quickMove(owner, RobotScreenHandler.PLAYER_START);
        context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 5,
                "Shift приоритетно направляет уголь в топливо");
        context.assertTrue(owner.getInventory().getStack(9).isEmpty(), "Перенос не дублирует уголь");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shiftMovesStorageBothWaysWithoutDuplication(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        owner.getInventory().setStack(9, new ItemStack(Items.STONE, 32));
        menu.quickMove(owner, RobotScreenHandler.PLAYER_START);
        context.assertTrue(robot.getInventory().getStack(0).getCount() == 32
                && owner.getInventory().count(Items.STONE) == 0, "Shift в хранение");
        menu.quickMove(owner, 0);
        context.assertTrue(robot.getInventory().isEmpty()
                && owner.getInventory().count(Items.STONE) == 32, "Shift обратно игроку без копирования");
        context.assertTrue(menu.quickMove(owner, -1).isEmpty() && menu.quickMove(owner, 999).isEmpty(),
                "Некорректный индекс Shift отклоняется");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void duplicateModulesRejectedIncludingAliasAndHotbarSwap(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        ItemStack module = new ItemStack(TestContent.MODULE);
        module.getOrCreateNbt().putInt("RoboHelper.Counter", 125);
        menu.setCursorStack(module);
        menu.onSlotClick(RobotInventory.FIRST_MODULE_SLOT, 0, SlotActionType.PICKUP, owner);
        context.assertTrue(menu.getCursorStack().isEmpty(), "Модуль установлен обычным кликом");
        menu.setCursorStack(new ItemStack(TestContent.MODULE_ALIAS));
        menu.onSlotClick(RobotInventory.FIRST_MODULE_SLOT + 1, 0, SlotActionType.PICKUP, owner);
        context.assertTrue(menu.getCursorStack().isOf(TestContent.MODULE_ALIAS)
                && robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT + 1).isEmpty(),
                "Разные предметы одной профессии не обходят запрет");
        owner.getInventory().setStack(0, menu.getCursorStack());
        menu.setCursorStack(ItemStack.EMPTY);
        menu.onSlotClick(RobotInventory.FIRST_MODULE_SLOT + 1, 0, SlotActionType.SWAP, owner);
        context.assertTrue(owner.getInventory().getStack(0).isOf(TestContent.MODULE_ALIAS)
                && robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT + 1).isEmpty(),
                "Обмен с хотбаром также проверяет профессию");
        owner.getInventory().setStack(9, new ItemStack(TestContent.MODULE));
        context.assertTrue(menu.quickMove(owner, RobotScreenHandler.PLAYER_START).isEmpty(),
                "Shift не вставляет дубликат");
        menu.quickMove(owner, RobotInventory.FIRST_MODULE_SLOT);
        ItemStack removed = owner.getInventory().main.stream().filter(stack -> stack.isOf(TestContent.MODULE)
                && stack.hasNbt()).findFirst().orElseThrow();
        context.assertTrue(removed.getNbt().getInt("RoboHelper.Counter") == 125
                && RobotModuleItem.getLevel(removed) == 2, "Снятие сохраняет прогресс и уровень");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeDragCannotInstallDuplicateProfession(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        owner.getAbilities().creativeMode = true;
        RobotScreenHandler menu = open(robot, owner);
        menu.setCursorStack(new ItemStack(TestContent.MODULE));
        menu.onSlotClick(-999, ScreenHandler.packQuickCraftData(0, 2), SlotActionType.QUICK_CRAFT, owner);
        menu.onSlotClick(RobotInventory.FIRST_MODULE_SLOT, ScreenHandler.packQuickCraftData(1, 2),
                SlotActionType.QUICK_CRAFT, owner);
        menu.onSlotClick(RobotInventory.FIRST_MODULE_SLOT + 1, ScreenHandler.packQuickCraftData(1, 2),
                SlotActionType.QUICK_CRAFT, owner);
        menu.onSlotClick(-999, ScreenHandler.packQuickCraftData(2, 2), SlotActionType.QUICK_CRAFT, owner);
        int count = 0;
        for (int slot = RobotInventory.FIRST_MODULE_SLOT; slot < RobotInventory.SIZE; slot++) {
            count += robot.getInventory().getStack(slot).getCount();
        }
        context.assertTrue(count == 1, "Даже творческое заполнение устанавливает только один модуль профессии");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void unloadDropsOverflowAndKeepsFuelAndModules(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        for (int slot = 0; slot < 36; slot++) {
            owner.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        }
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));
        robot.getInventory().setStack(1, new ItemStack(Items.DIRT, 4));
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL, 7));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(TestContent.MODULE));
        menu.onButtonClick(owner, RobotScreenHandler.UNLOAD_BUTTON);
        context.assertTrue(robot.getInventory().getStack(0).isEmpty() && robot.getInventory().getStack(1).isEmpty(),
                "Хранение выгружено");
        context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 7
                && robot.getInventory().getStack(RobotInventory.FIRST_MODULE_SLOT).isOf(TestContent.MODULE),
                "Топливо и профессия остались в роботе");
        context.assertTrue(dropCount(context, Items.DIAMOND) == 3 && dropCount(context, Items.DIRT) == 4,
                "Переполнение выпало на землю без потерь");
        menu.onButtonClick(owner, RobotScreenHandler.UNLOAD_BUTTON);
        context.assertTrue(dropCount(context, Items.DIAMOND) == 3 && dropCount(context, Items.DIRT) == 4,
                "Повторное нажатие не создаёт дополнительные предметы");
        owner.getAbilities().creativeMode = true;
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 2));
        menu.onButtonClick(owner, RobotScreenHandler.UNLOAD_BUTTON);
        context.assertTrue(dropCount(context, Items.DIAMOND) == 5,
                "Переполнение творческого инвентаря также выпадает, а не удаляется");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sneakingEmptyHandUnloadsWithoutOpeningScreen(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        robot.getInventory().setStack(0, new ItemStack(Items.OAK_LOG, 12));
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL, 2));
        owner.setSneaking(true);
        robot.interact(owner, Hand.MAIN_HAND);
        context.assertTrue(owner.currentScreenHandler == owner.playerScreenHandler, "Shift не открывает экран");
        context.assertTrue(owner.getInventory().count(Items.OAK_LOG) == 12
                && robot.getInventory().getStack(0).isEmpty(), "Shift выгружает хранение игроку");
        context.assertTrue(robot.getInventory().getStack(RobotInventory.FUEL_SLOT).getCount() == 2,
                "Shift не забирает топливо");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cursorItemReturnsOnClose(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 5));
        menu.onSlotClick(0, 0, SlotActionType.PICKUP, owner);
        context.assertTrue(menu.getCursorStack().getCount() == 5 && robot.getInventory().getStack(0).isEmpty(),
                "Предмет взят на курсор");
        owner.closeHandledScreen();
        context.assertTrue(menu.getCursorStack().isEmpty() && owner.getInventory().count(Items.DIAMOND) == 5,
                "Закрытие возвращает предмет игроку без потери и дублирования");
        owner.getAbilities().creativeMode = true;
        for (int slot = 0; slot < 36; slot++) {
            owner.getInventory().setStack(slot, new ItemStack(Items.STONE, 64));
        }
        owner.openHandledScreen(robot);
        RobotScreenHandler second = (RobotScreenHandler) owner.currentScreenHandler;
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 2));
        second.onSlotClick(0, 0, SlotActionType.PICKUP, owner);
        owner.closeHandledScreen();
        context.assertTrue(second.getCursorStack().isEmpty() && dropCount(context, Items.DIAMOND) == 2,
                "Закрытие с полным творческим инвентарём сохраняет остаток на земле");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void propertiesSyncAndClientValuesCannotChangeRobot(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler server = open(robot, owner);
        RobotScreenHandler copy = new RobotScreenHandler(server.syncId, owner.getInventory());
        server.addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slot, ItemStack stack) {
                copy.setStackInSlot(slot, 0, stack.copy());
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {
                copy.setProperty(property, value);
            }
        });
        robot.setEnergy(321);
        robot.setMode(RobotMode.WORK);
        robot.setMinerDirection(Direction.EAST);
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(TestContent.MINER));
        server.sendContentUpdates();
        context.assertTrue(copy.getEnergy() == 321 && copy.getMode() == RobotMode.WORK
                && copy.getStatus() == RobotStatus.WORKING && copy.hasMiner()
                && copy.getMinerDirection() == Direction.EAST, "Свойства доходят до копии контейнера");
        context.assertTrue(copy.getSlot(RobotInventory.FIRST_MODULE_SLOT).getStack().isOf(TestContent.MINER),
                "Предметы синхронизируются стандартным механизмом");
        server.setProperty(0, 6400);
        server.setProperty(1, RobotMode.IDLE.ordinal());
        context.assertTrue(robot.getEnergy() == 321 && robot.getMode() == RobotMode.WORK,
                "Входящие значения свойств не управляют сервером");
        finish(context, owner);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 60)
    public void distantOwnerScreenClosesAutomatically(TestContext context) {
        RobotEntity robot = robot(context);
        ServerPlayerEntity owner = owner(context, robot);
        RobotScreenHandler menu = open(robot, owner);
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 2));
        menu.onSlotClick(0, 0, SlotActionType.PICKUP, owner);
        owner.refreshPositionAndAngles(robot.getX() + 30, robot.getY(), robot.getZ(), 0, 0);
        context.runAtTick(5, () -> {
            context.assertTrue(owner.currentScreenHandler == owner.playerScreenHandler,
                    "Удаление от робота закрывает контейнер");
            context.assertTrue(owner.getInventory().count(Items.DIAMOND) == 2 && menu.getCursorStack().isEmpty(),
                    "Закрытие издалека сохраняет предмет с курсора");
            finish(context, owner);
        });
    }

    private static RobotEntity robot(TestContext context) {
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, POS);
        robot.setNoGravity(true);
        return robot;
    }

    private static ServerPlayerEntity owner(TestContext context, RobotEntity robot) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.getAbilities().creativeMode = false;
        player.refreshPositionAndAngles(robot.getX() + 1, robot.getY(), robot.getZ(), 0, 0);
        player.getInventory().clear();
        robot.setOwnerUuid(player.getUuid());
        return player;
    }

    private static RobotScreenHandler open(RobotEntity robot, ServerPlayerEntity player) {
        robot.interact(player, Hand.MAIN_HAND);
        return (RobotScreenHandler) player.currentScreenHandler;
    }

    private static int dropCount(TestContext context, Item item) {
        int count = 0;
        for (ItemEntity drop : context.getEntitiesAround(EntityType.ITEM, POS, 4)) {
            if (drop.getStack().isOf(item)) {
                count += drop.getStack().getCount();
            }
        }
        return count;
    }

    private static void finish(TestContext context, ServerPlayerEntity owner) {
        owner.closeHandledScreen();
        context.getWorld().getServer().getPlayerManager().remove(owner);
        context.complete();
    }
}
