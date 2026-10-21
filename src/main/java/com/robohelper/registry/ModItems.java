package com.robohelper.registry;

import com.robohelper.RoboHelper;
import com.robohelper.item.RobotSpawnItem;
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

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(RoboHelper.MOD_ID, "main"), FabricItemGroup.builder()
                    .displayName(Text.translatable("itemGroup.robohelper"))
                    .icon(() -> new ItemStack(ROBOT_BODY))
                    .entries((context, entries) -> entries.add(ROBOT_BODY))
                    .build());

    private ModItems() {
    }

    public static void initialize() {
        // Вызов обеспечивает загрузку и регистрацию статических полей.
    }
}
