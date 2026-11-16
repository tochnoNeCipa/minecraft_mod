package com.robohelper.registry;

import com.robohelper.RoboHelper;
import com.robohelper.item.RobotSpawnItem;
import com.robohelper.item.RobotModuleItem;
import com.robohelper.module.LumberjackModule;
import com.robohelper.module.FarmerModule;
import com.robohelper.module.LogisticsModule;
import com.robohelper.module.MinerModule;
import com.robohelper.module.GuardModule;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Предмет корпуса и отдельная вкладка RoboHelper в творческом режиме. */
public final class ModItems {
    public static final Item ROBOT_BODY = Registry.register(Registries.ITEM,
            new Identifier(RoboHelper.MOD_ID, "robot_body"), new RobotSpawnItem(new Item.Settings()));

    public static final Item LUMBERJACK_MODULE = Registry.register(Registries.ITEM,
            new Identifier(RoboHelper.MOD_ID, "lumberjack_module"),
            new RobotModuleItem(LumberjackModule.ID, new Item.Settings()));

    public static final Item FARMER_MODULE = Registry.register(Registries.ITEM,
            new Identifier(RoboHelper.MOD_ID, "farmer_module"), new RobotModuleItem(FarmerModule.ID, new Item.Settings()));

    public static final Item LOGISTICS_MODULE = Registry.register(Registries.ITEM,
            new Identifier(RoboHelper.MOD_ID, "logistics_module"), new RobotModuleItem(LogisticsModule.ID, new Item.Settings()));

    public static final Item MINER_MODULE = Registry.register(Registries.ITEM,
            new Identifier(RoboHelper.MOD_ID, "miner_module"), new RobotModuleItem(MinerModule.ID, new Item.Settings()));

    public static final Item GUARD_MODULE = Registry.register(Registries.ITEM,
            new Identifier(RoboHelper.MOD_ID, "guard_module"), new RobotModuleItem(GuardModule.ID, new Item.Settings()));

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(RoboHelper.MOD_ID, "main"), FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.robohelper"))
                    .icon(() -> new ItemStack(ROBOT_BODY))
                    .entries((context, entries) -> { entries.add(ROBOT_BODY); entries.add(LUMBERJACK_MODULE); entries.add(FARMER_MODULE); entries.add(LOGISTICS_MODULE); entries.add(MINER_MODULE); entries.add(GUARD_MODULE); })
                    .build());

    private ModItems() {
    }

    public static void initialize() {
        // Вызов обеспечивает загрузку и регистрацию статических полей.
    }
}
