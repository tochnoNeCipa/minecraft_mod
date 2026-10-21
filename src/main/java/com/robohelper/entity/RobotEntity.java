package com.robohelper.entity;

import com.robohelper.config.RobotConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.UUID;

/** Постоянная сущность робота: владелец, базовые свойства и собственные слоты. */
public final class RobotEntity extends PathAwareEntity {
    private final RobotInventory inventory = new RobotInventory(this);
    private UUID ownerUuid;

    public RobotEntity(EntityType<? extends RobotEntity> type, World world) {
        super(type, world);
        setPersistent();
        setCanPickUpLoot(false);
    }

    /** Атрибуты регистрируются до создания первого экземпляра сущности. */
    public static DefaultAttributeContainer.Builder createRobotAttributes() {
        return createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, RobotConfig.MAX_HEALTH)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, RobotConfig.MOVEMENT_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, RobotConfig.FOLLOW_RANGE)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, RobotConfig.ATTACK_DAMAGE);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, RobotConfig.LOOK_RANGE));
    }

    public RobotInventory getInventory() {
        return inventory;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public boolean isOwner(PlayerEntity player) {
        return ownerUuid != null && ownerUuid.equals(player.getUuid());
    }

    @Override
    public boolean canBeLeashedBy(PlayerEntity player) {
        return false;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (!isOwner(player)) {
            player.sendMessage(Text.translatable("message.robohelper.not_owner"), true);
            // MobEntity.interact иначе превращает FAIL в PASS и пробует вторую руку.
            return ActionResult.CONSUME;
        }
        ItemStack held = player.getStackInHand(hand);
        if (held.isEmpty()) {
            player.sendMessage(Text.translatable("message.robohelper.robot_status",
                    (int) getHealth(), (int) getMaxHealth()), true);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    @Override
    protected int computeFallDamage(float fallDistance, float damageMultiplier) {
        // Ванильная безопасная высота — 3 блока; робот переносит ещё 2 блока.
        return super.computeFallDamage(
                Math.max(0, fallDistance - (RobotConfig.SAFE_FALL_DISTANCE - 3.0F)), damageMultiplier);
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        // removeStack сразу очищает слот и исключает повторную выдачу того же предмета.
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.removeStack(slot);
            if (!stack.isEmpty()) {
                dropStack(stack);
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }
        NbtCompound inventoryNbt = new NbtCompound();
        inventory.writeNbt(inventoryNbt);
        nbt.put("Inventory", inventoryNbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        inventory.readNbt(nbt.getCompound("Inventory"));
        setPersistent();
    }
}
