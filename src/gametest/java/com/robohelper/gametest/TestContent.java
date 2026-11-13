package com.robohelper.gametest;

import com.robohelper.item.RobotModuleItem;
import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Тестовый предмет-модуль; не включается в распространяемый JAR. */
public final class TestContent implements ModInitializer {
    public static final Item MODULE = Registry.register(Registries.ITEM,
            new Identifier("robohelper_gametest", "module"), new RobotModuleItem(new Item.Settings()));

    public static final Item MODULE_ALIAS = Registry.register(Registries.ITEM,
            new Identifier("robohelper_gametest", "module_alias"), new RobotModuleItem(
                    new Identifier("robohelper_gametest", "module"), new Item.Settings()));
    public static final Item MINER = Registry.register(Registries.ITEM,
            new Identifier("robohelper_gametest", "miner"), new RobotModuleItem(
                    new Identifier("robohelper", "miner"), new Item.Settings()));

    public static final net.minecraft.util.Identifier PROBE_ID = new Identifier("robohelper_gametest", "probe");
    public static final Item PROBE = Registry.register(Registries.ITEM, PROBE_ID,
            new RobotModuleItem(PROBE_ID, new Item.Settings()));

    @Override
    public void onInitialize() {
        com.robohelper.module.ModuleRegistry.register(PROBE_ID, ProbeModule::new);
    }
}
