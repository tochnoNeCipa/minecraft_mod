package com.robohelper.entity;

import com.robohelper.ai.goal.FollowOwnerGoal;
import com.robohelper.ai.goal.ReturnToZoneGoal;
import com.robohelper.config.RobotConfig;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Постоянная сущность робота: владелец, базовые свойства и собственные слоты. */
public final class RobotEntity extends PathAwareEntity {
    private static final TrackedData<Integer> ENERGY = DataTracker.registerData(
            RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> MODE = DataTracker.registerData(
            RobotEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private BlockPos zoneCenter;
    private int zoneRadius = RobotConfig.ZONE_RADIUS;
    private boolean returningToZone;
    private int walkingTicks;
    private int lowEnergySignalTicks;

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
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(ENERGY, 0);
        dataTracker.startTracking(MODE, RobotMode.IDLE.ordinal());
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new ReturnToZoneGoal(this));
        goalSelector.add(5, new FollowOwnerGoal(this));
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

    /** Возвращает владельца только среди игроков текущего измерения. */
    @Nullable
    public PlayerEntity getOwnerPlayer() {
        return ownerUuid == null ? null : getWorld().getPlayerByUuid(ownerUuid);
    }

    /** Энергия синхронизируется стандартным DataTracker без собственных пакетов. */
    public int getEnergy() {
        return dataTracker.get(ENERGY);
    }

    /** Ограничивает энергию допустимой ёмкостью, в том числе при чтении NBT. */
    public void setEnergy(int energy) {
        dataTracker.set(ENERGY, MathHelper.clamp(energy, 0, RobotConfig.ENERGY_CAPACITY));
    }

    /** Атомарно списывает стоимость действия; при нехватке запас не изменяется. */
    public boolean tryConsumeEnergy(int cost) {
        if (cost < 0) {
            throw new IllegalArgumentException("Energy cost must not be negative");
        }
        if (getWorld().isClient || getEnergy() < cost) {
            return false;
        }
        setEnergy(getEnergy() - cost);
        return true;
    }

    /** Возвращает текущий режим, по умолчанию IDLE. */
    public RobotMode getMode() {
        return RobotMode.values()[dataTracker.get(MODE)];
    }

    /** Останавливает прежнее движение при смене режима. Проверка владельца — у вызывающего GUI. */
    public void setMode(RobotMode mode) {
        dataTracker.set(MODE, mode.ordinal());
        getNavigation().stop();
        setTarget(null);
        returningToZone = false;
        walkingTicks = 0;
    }

    /** Общий порог запуска рабочих целей; FOLLOW от энергии не зависит. */
    public boolean canWork() {
        return isAlive() && getMode() == RobotMode.WORK && getEnergy() > RobotConfig.LOW_ENERGY_THRESHOLD;
    }

    /** Профессия не может начать действие до завершения возвращения в зону. */
    public boolean canStartModuleWork() {
        return canWork() && !requiresZoneReturn();
    }

    /** До первого тика центр берётся из позиции появления. */
    public BlockPos getZoneCenter() {
        return zoneCenter == null ? getBlockPos() : zoneCenter;
    }

    /** Переносит центр зоны и сбрасывает путь к прежнему центру. */
    public void setZoneCenter(BlockPos center) {
        zoneCenter = center.toImmutable();
        returningToZone = false;
        getNavigation().stop();
    }

    /** Радиус по горизонтали; вертикальная граница задаётся RobotConfig. */
    public int getZoneRadius() {
        return zoneRadius;
    }

    /** Цилиндрическая зона: горизонтальный радиус включительно и ±8 блоков по Y. */
    public boolean isInsideWorkZone(BlockPos pos) {
        BlockPos center = getZoneCenter();
        long dx = (long) pos.getX() - center.getX();
        long dz = (long) pos.getZ() - center.getZ();
        long dy = Math.abs((long) pos.getY() - center.getY());
        return Math.abs(dx) <= zoneRadius && Math.abs(dz) <= zoneRadius
                && dx * dx + dz * dz <= (long) zoneRadius * zoneRadius
                && dy <= RobotConfig.ZONE_VERTICAL_RADIUS;
    }

    /** Возвращение продолжается до центра, а не только до ближайшей границы зоны. */
    public boolean requiresZoneReturn() {
        return returningToZone || !isInsideWorkZone(getBlockPos());
    }

    /** Внутренний флаг цели возвращения; текущая задача на диск не записывается. */
    public void setReturningToZone(boolean returning) {
        returningToZone = returning;
    }

    /** Допустимое отклонение навигации от центра зоны. */
    public boolean isNearZoneCenter() {
        BlockPos center = getZoneCenter();
        return squaredDistanceTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5)
                <= RobotConfig.RETURN_ARRIVAL_DISTANCE * RobotConfig.RETURN_ARRIVAL_DISTANCE;
    }

    @Override
    public void tick() {
        double previousX = getX();
        double previousZ = getZ();
        super.tick();
        if (getWorld().isClient || !isAlive()) {
            return;
        }
        if (zoneCenter == null) {
            zoneCenter = getBlockPos().toImmutable();
        }
        refuel();
        if (getMode() == RobotMode.WORK) {
            double dx = getX() - previousX;
            double dz = getZ() - previousZ;
            if (canWork() && !getNavigation().isIdle() && dx * dx + dz * dz > 0.000001) {
                if (++walkingTicks >= RobotConfig.TICKS_PER_SECOND) {
                    tryConsumeEnergy(RobotConfig.WALK_ENERGY_COST);
                    walkingTicks = 0;
                }
            }
            if (!canWork()) {
                getNavigation().stop();
                if (++lowEnergySignalTicks >= RobotConfig.LOW_ENERGY_SIGNAL_INTERVAL) {
                    lowEnergySignalTicks = 0;
                    ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.SMOKE,
                            getX(), getBodyY(0.8), getZ(), 4, 0.15, 0.1, 0.15, 0.01);
                    playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.2F, 1.5F);
                }
            } else {
                lowEnergySignalTicks = 0;
            }
        } else {
            walkingTicks = 0;
            lowEnergySignalTicks = 0;
        }
    }

    private void refuel() {
        ItemStack fuel = inventory.getStack(RobotInventory.FUEL_SLOT);
        if (fuel.isEmpty()) {
            return;
        }
        Integer value = FuelRegistry.INSTANCE.get(fuel.getItem());
        // Топливо дороже полной ёмкости остаётся в слоте: энергия не теряется.
        if (value == null || value <= 0 || value > RobotConfig.ENERGY_CAPACITY - getEnergy()) {
            return;
        }
        ItemStack remainder = fuel.getItem().hasRecipeRemainder()
                ? new ItemStack(fuel.getItem().getRecipeRemainder()) : ItemStack.EMPTY;
        fuel.decrement(1);
        if (fuel.isEmpty()) {
            inventory.setStack(RobotInventory.FUEL_SLOT, remainder);
        } else if (!remainder.isEmpty()) {
            ItemStack excess = inventory.insertStorage(remainder);
            if (!excess.isEmpty()) {
                dropStack(excess);
            }
        }
        inventory.markDirty();
        setEnergy(getEnergy() + value);
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
                    (int) getHealth(), (int) getMaxHealth(), getEnergy(), RobotConfig.ENERGY_CAPACITY,
                    Text.translatable("mode.robohelper." + getMode().name().toLowerCase(java.util.Locale.ROOT))), true);
            return ActionResult.CONSUME;
        }
        if (inventory.isValid(RobotInventory.FUEL_SLOT, held)) {
            ItemStack fuel = inventory.getStack(RobotInventory.FUEL_SLOT);
            int space = fuel.isEmpty() ? held.getMaxCount()
                    : ItemStack.canCombine(fuel, held) ? fuel.getMaxCount() - fuel.getCount() : 0;
            int amount = Math.min(space, held.getCount());
            if (amount > 0) {
                if (fuel.isEmpty()) {
                    inventory.setStack(RobotInventory.FUEL_SLOT, held.copyWithCount(amount));
                } else {
                    fuel.increment(amount);
                    inventory.markDirty();
                }
                if (!player.getAbilities().creativeMode) {
                    held.decrement(amount);
                }
            } else {
                player.sendMessage(Text.translatable("message.robohelper.fuel_slot_full"), true);
            }
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
        nbt.putInt("Energy", getEnergy());
        nbt.putString("Mode", getMode().name());
        BlockPos center = getZoneCenter();
        nbt.putIntArray("ZoneCenter", new int[]{center.getX(), center.getY(), center.getZ()});
        nbt.putInt("ZoneRadius", zoneRadius);
        NbtCompound inventoryNbt = new NbtCompound();
        inventory.writeNbt(inventoryNbt);
        nbt.put("Inventory", inventoryNbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        inventory.readNbt(nbt.getCompound("Inventory"));
        setEnergy(nbt.getInt("Energy"));
        setMode(RobotMode.fromNbt(nbt.getString("Mode")));
        int[] center = nbt.getIntArray("ZoneCenter");
        setZoneCenter(center.length == 3 ? new BlockPos(center[0], center[1], center[2]) : getBlockPos());
        zoneRadius = nbt.contains("ZoneRadius", NbtElement.NUMBER_TYPE)
                ? MathHelper.clamp(nbt.getInt("ZoneRadius"), 1, RobotConfig.ZONE_RADIUS) : RobotConfig.ZONE_RADIUS;
        lowEnergySignalTicks = 0;
        setPersistent();
    }
}
