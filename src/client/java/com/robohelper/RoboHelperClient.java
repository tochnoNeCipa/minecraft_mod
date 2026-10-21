package com.robohelper;

import com.robohelper.client.RobotRenderer;
import com.robohelper.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Клиентская точка входа для будущей регистрации экрана и рендера робота. */
public final class RoboHelperClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ROBOT, RobotRenderer::new);
        RoboHelper.LOGGER.info("RoboHelper client initialized");
    }
}
