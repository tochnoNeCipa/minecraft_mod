package com.robohelper;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Общая точка входа RoboHelper для клиента и выделенного сервера. */
public final class RoboHelper implements ModInitializer {
    public static final String MOD_ID = "robohelper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("RoboHelper initialized");
    }
}
