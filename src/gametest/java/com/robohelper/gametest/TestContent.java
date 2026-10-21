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

    @Override
    public void onInitialize() {
    }
}
