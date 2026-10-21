package com.robohelper.item;

import net.minecraft.item.Item;

/** Базовый предмет профессии; только такие предметы принимаются слотами модулей. */
public class RobotModuleItem extends Item {
    public RobotModuleItem(Settings settings) {
        super(settings.maxCount(1));
    }
}
