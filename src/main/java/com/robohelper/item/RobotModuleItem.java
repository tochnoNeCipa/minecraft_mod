package com.robohelper.item;

import com.robohelper.config.RobotConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/** Базовый предмет профессии с устойчивым идентификатором и читаемым прогрессом. */
public class RobotModuleItem extends Item {
    @Nullable
    private final Identifier moduleId;

    public RobotModuleItem(Settings settings) {
        this(null, settings);
    }

    public RobotModuleItem(@Nullable Identifier moduleId, Settings settings) {
        super(settings.maxCount(1));
        this.moduleId = moduleId;
    }

    /** Один идентификатор профессии не позволяет установить её дважды разными предметами. */
    public Identifier getModuleId() {
        return moduleId == null ? Registries.ITEM.getId(this) : moduleId;
    }

    /** Читает уровень из сохранённого счётчика, не изменяя предмет. */
    public static int getLevel(ItemStack stack) {
        int counter = stack.hasNbt() ? stack.getNbt().getInt("RoboHelper.Counter") : 0;
        return counter < RobotConfig.MODULE_LEVEL_TWO_ACTIONS ? 1
                : counter < RobotConfig.MODULE_LEVEL_THREE_ACTIONS ? 2 : 3;
    }
}
