package com.robohelper.screen;

/** Короткое состояние задачи, синхронизируемое с экраном как целое число. */
public enum RobotStatus {
    IDLE,
    FOLLOWING,
    OWNER_UNAVAILABLE,
    LOW_ENERGY,
    RETURNING,
    NO_MODULES,
    WORKING,
    SEARCHING_TREE,
    CHOPPING_TREE,
    PLANTING_TREE,
    STORAGE_FULL,
    SEARCHING_CROP,
    HARVESTING,
    PLANTING_CROP,
    SEARCHING_CONTAINER,
    UNLOADING,
    NO_CONTAINER,
    MINING,
    MINING_BLOCKED,
    TUNNEL_COMPLETE,
    GUARDING
}
