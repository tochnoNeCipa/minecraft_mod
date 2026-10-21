package com.robohelper.registry;

import com.robohelper.RoboHelper;
import com.robohelper.config.RobotConfig;
import com.robohelper.entity.RobotEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Регистрация типа робота и обязательного набора атрибутов. */
public final class ModEntities {
    public static final EntityType<RobotEntity> ROBOT = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(RoboHelper.MOD_ID, "robot"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, RobotEntity::new)
                    .dimensions(EntityDimensions.fixed(RobotConfig.WIDTH, RobotConfig.HEIGHT))
                    .trackRangeBlocks(64).build());

    private ModEntities() {
    }

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(ROBOT, RobotEntity.createRobotAttributes());
    }
}
