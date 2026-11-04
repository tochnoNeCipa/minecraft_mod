package com.robohelper.config;

/** Общие параметры робота; игровые значения собраны в одном месте. */
public final class RobotConfig {
    public static final double MAX_HEALTH = 40.0;
    public static final double MOVEMENT_SPEED = 0.3;
    public static final double FOLLOW_RANGE = 32.0;
    public static final double ATTACK_DAMAGE = 4.0;
    public static final float WIDTH = 0.6F;
    public static final float HEIGHT = 1.8F;
    public static final float SAFE_FALL_DISTANCE = 5.0F;
    public static final int STORAGE_SLOTS = 18;
    public static final int MODULE_SLOTS = 3;
    public static final int MODULE_LEVEL_TWO_ACTIONS = 100;
    public static final int MODULE_LEVEL_THREE_ACTIONS = 500;
    public static final double INTERACTION_DISTANCE_SQUARED = 64.0;
    public static final float LOOK_RANGE = 8.0F;
    public static final float SHADOW_RADIUS = 0.4F;

    public static final int ENERGY_CAPACITY = 6400;
    public static final int LOW_ENERGY_THRESHOLD = ENERGY_CAPACITY / 20;
    public static final int WALK_ENERGY_COST = 1;
    public static final int TICKS_PER_SECOND = 20;
    public static final int LOW_ENERGY_SIGNAL_INTERVAL = 100;
    public static final int ZONE_RADIUS = 15;
    public static final int ZONE_VERTICAL_RADIUS = 8;
    public static final double FOLLOW_START_DISTANCE = 6.0;
    public static final double FOLLOW_STOP_DISTANCE = 3.0;
    public static final double FOLLOW_TELEPORT_DISTANCE = 24.0;
    public static final double NAVIGATION_SPEED = 1.0;
    public static final int FOLLOW_REPATH_INTERVAL = 10;
    public static final int RETURN_RETRY_INTERVAL = 40;
    public static final double RETURN_ARRIVAL_DISTANCE = 1.5;
    public static final int TELEPORT_RADIUS = 2;

    private RobotConfig() {
    }
}
