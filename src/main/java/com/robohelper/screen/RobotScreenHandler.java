package com.robohelper.screen;

import com.robohelper.config.RobotConfig;
import com.robohelper.inventory.InventoryTransfer;
import com.robohelper.entity.RobotEntity;
import com.robohelper.entity.RobotInventory;
import com.robohelper.entity.RobotMode;
import com.robohelper.registry.ModScreens;
import com.robohelper.item.RobotModuleItem;
import net.minecraft.text.Text;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/** Серверный контейнер: слоты, свойства и проверенные действия стандартных кнопок. */
public final class RobotScreenHandler extends ScreenHandler {
    public static final int MODE_BUTTON = 0;
    public static final int UNLOAD_BUTTON = 1;
    public static final int ZONE_BUTTON = 2;
    public static final int DIRECTION_BUTTON = 3;
    public static final int PROPERTY_COUNT = 5;
    public static final int PLAYER_START = RobotInventory.SIZE;
    public static final int PLAYER_END = PLAYER_START + 36;
    public static final Identifier MINER_ID = new Identifier("robohelper", "miner");

    private final Inventory inventory;
    private final PropertyDelegate properties;
    @Nullable
    private final RobotEntity robot;

    /** Клиентская копия содержит только синхронизируемые слоты и свойства. */
    public RobotScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, new SimpleInventory(RobotInventory.SIZE),
                new ArrayPropertyDelegate(PROPERTY_COUNT), null);
    }

    /** Серверная копия использует настоящий инвентарь выбранного робота. */
    public RobotScreenHandler(int syncId, PlayerInventory playerInventory, RobotEntity robot) {
        this(syncId, playerInventory, robot.getInventory(), serverProperties(robot), robot);
    }

    private RobotScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                               PropertyDelegate properties, @Nullable RobotEntity robot) {
        super(ModScreens.ROBOT, syncId);
        checkSize(inventory, RobotInventory.SIZE);
        checkDataCount(properties, PROPERTY_COUNT);
        this.inventory = inventory;
        this.properties = properties;
        this.robot = robot;
        inventory.onOpen(playerInventory.player);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addRobotSlot(row * 9 + column, 57 + column * 18, 70 + row * 18);
            }
        }
        addRobotSlot(RobotInventory.FUEL_SLOT, 216, 31);
        for (int module = 0; module < RobotConfig.MODULE_SLOTS; module++) {
            addRobotSlot(RobotInventory.FIRST_MODULE_SLOT + module, 12 + module * 24, 31);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, 9 + row * 9 + column, 57 + column * 18, 146 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 57 + column * 18, 204));
        }
        addProperties(properties);
    }

    private void addRobotSlot(int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return RobotInventory.canInsert(inventory, index, stack);
            }

            @Override
            public int getMaxItemCount() {
                return index >= RobotInventory.FIRST_MODULE_SLOT ? 1 : super.getMaxItemCount();
            }

            @Override
            public boolean canTakeItems(PlayerEntity player) {
                return canUse(player);
            }
        });
    }

    private static PropertyDelegate serverProperties(RobotEntity robot) {
        return new PropertyDelegate() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> robot.getEnergy();
                    case 1 -> robot.getMode().ordinal();
                    case 2 -> robot.getStatus().ordinal();
                    case 3 -> robot.getMinerDirection().getHorizontal();
                    case 4 -> robot.getInventory().hasModule(MINER_ID) ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Значения клиента никогда не записываются в состояние серверной сущности.
            }

            @Override
            public int size() {
                return PROPERTY_COUNT;
            }
        };
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return robot == null ? player.getWorld().isClient : inventory.canPlayerUse(player);
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!canUse(player)) {
            return;
        }
        ItemStack candidate = getCursorStack();
        if (actionType == SlotActionType.SWAP && (button >= 0 && button < 9 || button == 40)) {
            candidate = player.getInventory().getStack(button);
        }
        if (slotIndex >= RobotInventory.FIRST_MODULE_SLOT && slotIndex < RobotInventory.SIZE
                && candidate.getItem() instanceof RobotModuleItem
                && !RobotInventory.canInsert(inventory, slotIndex, candidate)) {
            player.sendMessage(Text.translatable("message.robohelper.duplicate_module"), true);
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slotIndex) {
        if (!canUse(player) || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(slotIndex);
        if (!slot.hasStack() || !slot.canTakeItems(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getStack();
        ItemStack original = source.copy();
        boolean moved;
        if (slotIndex < PLAYER_START) {
            moved = insertItem(source, PLAYER_START, PLAYER_END, true);
        } else if (source.getItem() instanceof RobotModuleItem module) {
            if (robot != null && robot.getInventory().hasModule(module.getModuleId())) {
                player.sendMessage(Text.translatable("message.robohelper.duplicate_module"), true);
            }
            moved = insertItem(source, RobotInventory.FIRST_MODULE_SLOT, RobotInventory.SIZE, false);
        } else {
            moved = false;
            if (RobotInventory.canInsert(inventory, RobotInventory.FUEL_SLOT, source)) {
                moved = insertItem(source, RobotInventory.FUEL_SLOT, RobotInventory.FUEL_SLOT + 1, false);
            }
            if (!source.isEmpty()) {
                moved |= insertItem(source, 0, RobotConfig.STORAGE_SLOTS, false);
            }
        }
        if (!moved || source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        slot.onTakeItem(player, source);
        return original;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (robot == null || !canUse(player)) {
            return false;
        }
        switch (id) {
            case MODE_BUTTON -> robot.setMode(robot.getMode().next());
            case UNLOAD_BUTTON -> robot.unloadStorage(player);
            case ZONE_BUTTON -> robot.setZoneCenter(robot.getBlockPos());
            case DIRECTION_BUTTON -> {
                if (!robot.getInventory().hasModule(MINER_ID)) {
                    return false;
                }
                robot.setMinerDirection(robot.getHorizontalFacing());
            }
            default -> {
                return false;
            }
        }
        sendContentUpdates();
        return true;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        if (!player.getWorld().isClient) {
            ItemStack cursor = getCursorStack();
            setCursorStack(ItemStack.EMPTY);
            ItemStack remaining = player.isAlive()
                    ? InventoryTransfer.insert(player.getInventory(), cursor, 0, player.getInventory().main.size())
                    : cursor;
            if (!remaining.isEmpty()) {
                player.dropItem(remaining, false);
            }
        }
        super.onClosed(player);
        inventory.onClose(player);
    }

    /** Заряд для полосы и подсказки; сетевые значения ограничиваются на клиенте. */
    public int getEnergy() {
        return Math.max(0, Math.min(RobotConfig.ENERGY_CAPACITY, properties.get(0)));
    }

    /** Режим кнопки из стандартного PropertyDelegate. */
    public RobotMode getMode() {
        int value = properties.get(1);
        return value >= 0 && value < RobotMode.values().length ? RobotMode.values()[value] : RobotMode.IDLE;
    }

    /** Короткий статус для строки задачи. */
    public RobotStatus getStatus() {
        int value = properties.get(2);
        return value >= 0 && value < RobotStatus.values().length ? RobotStatus.values()[value] : RobotStatus.IDLE;
    }

    /** Кнопка направления доступна только с установленным шахтёром. */
    public boolean hasMiner() {
        return properties.get(4) != 0;
    }

    /** Сохранённая сторона света для подсказки кнопки. */
    public Direction getMinerDirection() {
        return Direction.fromHorizontal(properties.get(3));
    }
}
