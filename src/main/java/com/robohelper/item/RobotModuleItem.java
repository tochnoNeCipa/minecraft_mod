package com.robohelper.item;

import com.robohelper.config.RobotConfig;
import com.robohelper.module.ModuleProgress;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import java.util.List;
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
        return ModuleProgress.level(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.robohelper.level", getLevel(stack)).formatted(Formatting.AQUA));
        int target = getLevel(stack) == 1 ? RobotConfig.MODULE_LEVEL_TWO_ACTIONS : RobotConfig.MODULE_LEVEL_THREE_ACTIONS;
        tooltip.add((getLevel(stack) == 3
                ? Text.translatable("tooltip.robohelper.max_level", ModuleProgress.counter(stack))
                : Text.translatable("tooltip.robohelper.progress", ModuleProgress.counter(stack), target))
                .formatted(Formatting.GRAY));
    }
}
