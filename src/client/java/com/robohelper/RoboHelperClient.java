package com.robohelper;

import com.robohelper.client.RobotRenderer;
import com.robohelper.registry.ModEntities;
import com.robohelper.registry.ModScreens;
import com.robohelper.screen.RobotScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Клиентская точка входа: регистрация экрана и рендера робота. */
public final class RoboHelperClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ROBOT, RobotRenderer::new);
        HandledScreens.register(ModScreens.ROBOT, RobotScreen::new);
        RoboHelper.LOGGER.info("RoboHelper client initialized");
    }
}
