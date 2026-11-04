package com.robohelper.screen;

/** Короткое состояние задачи, синхронизируемое с экраном как целое число. */
public enum RobotStatus {
    IDLE,
    FOLLOWING,
    OWNER_UNAVAILABLE,
    LOW_ENERGY,
    RETURNING,
    NO_MODULES,
    WORKING
}
