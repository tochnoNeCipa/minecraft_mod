package com.robohelper.gametest;

import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Интеграционные проверки на настоящем сервере Minecraft без графики. */
public final class RobotTests implements FabricGameTest {
    private static final BlockPos BASE = new BlockPos(2, 1, 2);
    private static final BlockPos ROBOT_POS = BASE.up();

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void survivalSpawn(TestContext context) {
        context.setBlockState(BASE, Blocks.STONE);
        PlayerEntity owner = context.createMockSurvivalPlayer();
        ItemStack body = new ItemStack(ModItems.ROBOT_BODY, 2);
        context.assertTrue(useBody(context, owner, body).isAccepted(), "Корпус должен создать робота");
        RobotEntity robot = context.getEntitiesAround(ModEntities.ROBOT, ROBOT_POS, 1).get(0);
        context.assertTrue(body.getCount() == 1, "В выживании расходуется один корпус");
        context.assertTrue(robot.isOwner(owner), "Владелец должен назначаться создателю");
        context.assertTrue(robot.getHealth() == 40 && robot.getMaxHealth() == 40, "Здоровье 40");
        context.assertTrue(robot.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) == 0.3,
                "Скорость 0.3");
        context.assertTrue(robot.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE) == 4,
                "Урон 4");
        context.assertTrue(robot.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE) == 32,
                "Обзор 32");
        context.assertTrue(robot.isPersistent(), "Робот не должен исчезать от удаления игрока");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blockedSpawnDoesNotConsumeBody(TestContext context) {
        context.setBlockState(BASE, Blocks.STONE);
        context.setBlockState(ROBOT_POS.up(), Blocks.STONE);
        PlayerEntity owner = context.createMockSurvivalPlayer();
        ItemStack body = new ItemStack(ModItems.ROBOT_BODY, 2);
        context.assertTrue(useBody(context, owner, body) == ActionResult.FAIL,
                "Недостаточно места по высоте: создание должно отклоняться");
        context.assertTrue(body.getCount() == 2, "При отказе корпус не расходуется");
        context.assertTrue(context.getEntitiesAround(ModEntities.ROBOT, ROBOT_POS, 2).isEmpty(),
                "Робот не должен появиться внутри блока");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeSpawnKeepsBody(TestContext context) {
        context.setBlockState(BASE, Blocks.STONE);
        PlayerEntity owner = context.createMockCreativePlayer();
        // Ванильный mock переопределяет isCreative, но не настраивает PlayerAbilities.
        owner.getAbilities().creativeMode = true;
        ItemStack body = new ItemStack(ModItems.ROBOT_BODY);
        context.assertTrue(useBody(context, owner, body).isAccepted(), "Создание в творческом режиме");
        context.assertTrue(body.getCount() == 1, "В творческом режиме корпус не расходуется");
        context.expectEntityAround(ModEntities.ROBOT, ROBOT_POS, 1);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nbtRoundTrip(TestContext context) {
        RobotEntity original = context.spawnEntity(ModEntities.ROBOT, ROBOT_POS);
        UUID owner = UUID.randomUUID();
        original.setOwnerUuid(owner);
        original.setHealth(23);
        RobotInventory inventory = original.getInventory();
        for (int slot = 0; slot < 18; slot++) {
            ItemStack stack = new ItemStack(Items.OAK_LOG, slot + 1);
            stack.getOrCreateNbt().putInt("TestSlot", slot);
            inventory.setStack(slot, stack);
        }
        inventory.setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL, 12));
        for (int slot = RobotInventory.FIRST_MODULE_SLOT; slot < inventory.size(); slot++) {
            ItemStack module = new ItemStack(TestContent.MODULE);
            module.getOrCreateNbt().putInt("RoboHelper.Counter", slot * 10);
            inventory.setStack(slot, module);
        }
        NbtCompound saved = original.writeNbt(new NbtCompound());
        RobotEntity restored = ModEntities.ROBOT.create(context.getWorld());
        context.assertTrue(restored != null, "Сущность должна создаваться из зарегистрированного типа");
        restored.readNbt(saved);
        context.assertTrue(owner.equals(restored.getOwnerUuid()), "UUID владельца должен сохраниться");
        context.assertTrue(restored.isPersistent(), "Постоянство после загрузки");
        context.assertTrue(restored.getHealth() == 23, "Здоровье после загрузки");
        for (int slot = 0; slot < inventory.size(); slot++) {
            context.assertTrue(ItemStack.areEqual(inventory.getStack(slot), restored.getInventory().getStack(slot)),
                    "Предмет, количество и NBT должны сохраниться в слоте " + slot);
        }
        // Повторная загрузка очищает отсутствующие в новом снимке слоты.
        original.getInventory().clear();
        restored.readNbt(original.writeNbt(new NbtCompound()));
        context.assertTrue(restored.getInventory().isEmpty(), "Пустые слоты не должны сохранять старые предметы");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void ownershipAndDistance(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, ROBOT_POS);
        PlayerEntity owner = context.createMockSurvivalPlayer();
        PlayerEntity other = context.createMockSurvivalPlayer();
        other.setUuid(UUID.randomUUID());
        owner.refreshPositionAndAngles(robot.getX() + 1, robot.getY(), robot.getZ(), 0, 0);
        other.refreshPositionAndAngles(robot.getX() + 1, robot.getY(), robot.getZ(), 0, 0);
        robot.setOwnerUuid(owner.getUuid());
        context.assertTrue(robot.getInventory().canPlayerUse(owner), "Владелец рядом имеет доступ");
        context.assertFalse(robot.getInventory().canPlayerUse(other), "Чужой игрок не имеет доступа");
        context.assertTrue(robot.interact(other, Hand.MAIN_HAND) == ActionResult.CONSUME,
                "Отказ чужому игроку обрабатывается без повторной попытки второй рукой");
        context.assertTrue(robot.getOwnerUuid().equals(owner.getUuid()), "Чужой игрок не меняет владельца");
        context.assertTrue(robot.getInventory().isEmpty(), "Чужое взаимодействие не меняет слоты");
        context.assertTrue(robot.interact(owner, Hand.MAIN_HAND).isAccepted(), "Взаимодействие владельца");
        owner.refreshPositionAndAngles(robot.getX() + 20, robot.getY(), robot.getZ(), 0, 0);
        context.assertFalse(robot.getInventory().canPlayerUse(owner), "Нет доступа издалека");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void slotRestrictions(TestContext context) {
        RobotInventory inventory = context.spawnEntity(ModEntities.ROBOT, ROBOT_POS).getInventory();
        context.assertTrue(inventory.size() == 22, "Ровно 22 слота");
        context.assertTrue(inventory.isValid(0, new ItemStack(Items.STONE)), "Хранение любых предметов");
        context.assertTrue(inventory.isValid(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL)), "Уголь — топливо");
        context.assertFalse(inventory.isValid(RobotInventory.FUEL_SLOT, new ItemStack(Items.STONE)),
                "Камень не принимается как топливо");
        context.assertTrue(inventory.isValid(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(TestContent.MODULE)),
                "Слот принимает предмет-модуль");
        context.assertFalse(inventory.isValid(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(Items.IRON_AXE)),
                "Обычный инструмент не является модулем");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void deathDropsEverySlotOnce(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, ROBOT_POS);
        robot.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));
        robot.getInventory().setStack(RobotInventory.FUEL_SLOT, new ItemStack(Items.COAL, 7));
        robot.getInventory().setStack(RobotInventory.FIRST_MODULE_SLOT, new ItemStack(TestContent.MODULE));
        robot.damage(context.getWorld().getDamageSources().genericKill(), 1000);
        Map<Item, Integer> counts = new HashMap<>();
        for (ItemEntity drop : context.getEntitiesAround(EntityType.ITEM, ROBOT_POS, 3)) {
            counts.merge(drop.getStack().getItem(), drop.getStack().getCount(), Integer::sum);
        }
        context.assertTrue(counts.equals(Map.of(Items.DIAMOND, 3, Items.COAL, 7, TestContent.MODULE, 1)),
                "При гибели выпадают хранение, топливо и модули без потерь и дублирования");
        context.assertTrue(robot.getInventory().isEmpty(), "После выпадения слоты пусты");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fallProtection(TestContext context) {
        RobotEntity robot = context.spawnEntity(ModEntities.ROBOT, ROBOT_POS);
        robot.handleFallDamage(5, 1, context.getWorld().getDamageSources().fall());
        context.assertTrue(robot.getHealth() == 40, "Падение с 5 блоков безопасно");
        robot.handleFallDamage(6, 1, context.getWorld().getDamageSources().fall());
        context.assertTrue(robot.getHealth() == 39, "Выше 5 блоков урон сохраняется");
        context.complete();
    }

    private ActionResult useBody(TestContext context, PlayerEntity player, ItemStack body) {
        player.setStackInHand(Hand.MAIN_HAND, body);
        BlockPos absolute = context.getAbsolutePos(BASE);
        return body.useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(absolute).add(0, 0.5, 0), Direction.UP, absolute, false)));
    }
}
