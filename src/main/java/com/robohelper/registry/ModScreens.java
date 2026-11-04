package com.robohelper.registry;

import com.robohelper.RoboHelper;
import com.robohelper.screen.RobotScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/** Тип стандартного контейнера; клиентские классы на сервере не загружаются. */
public final class ModScreens {
    public static final ScreenHandlerType<RobotScreenHandler> ROBOT = Registry.register(
            Registries.SCREEN_HANDLER, new Identifier(RoboHelper.MOD_ID, "robot"),
            new ScreenHandlerType<>(RobotScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    private ModScreens() {
    }

    /** Загружает регистрацию при запуске общей части мода. */
    public static void initialize() {
    }
}
