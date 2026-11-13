package com.robohelper.inventory;

import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotInventory;
import net.minecraft.block.SaplingBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Пересчитывает выгружаемые остатки с квотой по виду, а не по отдельной стопке. */
public final class LogisticsCargo {
    private static final Set<Item> SEEDS = Set.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.CARROT,
            Items.POTATO, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD);

    private LogisticsCargo() {
    }

    /** Порог считается только по занятым ячейкам хранения, без топлива и модулей. */
    public static boolean needsUnload(RobotInventory inventory) {
        int occupied = 0;
        for (int slot = 0; slot < RobotConfig.STORAGE_SLOTS; slot++) {
            if (!inventory.getStack(slot).isEmpty()) {
                occupied++;
            }
        }
        return occupied * 100 >= RobotConfig.STORAGE_SLOTS * RobotConfig.LOGISTICS_THRESHOLD_PERCENT;
    }

    /** Топливный слот и установленные модули не входят в груз. Брёвна являются грузом. */
    public static List<Cargo> exports(RobotInventory inventory) {
        Map<Item, Integer> reserves = new HashMap<>();
        List<Cargo> cargo = new ArrayList<>();
        for (int slot = 0; slot < RobotConfig.STORAGE_SLOTS; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int kept = 0;
            if (SEEDS.contains(stack.getItem()) || stack.getItem() instanceof BlockItem block
                    && block.getBlock() instanceof SaplingBlock) {
                int quota = reserves.getOrDefault(stack.getItem(), RobotConfig.PLANTING_RESERVE);
                kept = Math.min(quota, stack.getCount());
                reserves.put(stack.getItem(), quota - kept);
            }
            if (stack.getCount() > kept) {
                cargo.add(new Cargo(slot, stack.copyWithCount(stack.getCount() - kept)));
            }
        }
        return cargo;
    }

    /** Для выбора контейнера достаточно места хотя бы для одного предмета из стопки. */
    public static boolean canAccept(Inventory inventory, ItemStack stack) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack existing = inventory.getStack(slot);
            if (inventory.isValid(slot, stack) && (existing.isEmpty() || ItemStack.canCombine(existing, stack)
                    && existing.getCount() < Math.min(existing.getMaxCount(), inventory.getMaxCountPerStack()))) {
                return true;
            }
        }
        return false;
    }

    /** Снимок разрешённой части одного слота; пересчитывается перед каждым переносом. */
    public record Cargo(int slot, ItemStack stack) {
    }
}
