package com.robohelper;

import net.fabricmc.api.ClientModInitializer;

/** Клиентская точка входа для будущей регистрации экрана и рендера робота. */
public final class RoboHelperClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RoboHelper.LOGGER.info("RoboHelper client initialized");
    }
}
