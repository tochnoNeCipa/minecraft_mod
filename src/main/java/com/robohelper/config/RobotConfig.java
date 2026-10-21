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
    public static final double INTERACTION_DISTANCE_SQUARED = 64.0;
    public static final float LOOK_RANGE = 8.0F;
    public static final float SHADOW_RADIUS = 0.4F;

    private RobotConfig() {
    }
}
