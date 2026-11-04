package com.robohelper.entity;

/** Режим управления; порядок соответствует циклу кнопки в интерфейсе. */
public enum RobotMode {
    WORK,
    IDLE,
    FOLLOW;

    /** Возвращает следующий режим WORK → IDLE → FOLLOW → WORK. */
    public RobotMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Старые или повреждённые данные безопасно переводят робота в ожидание. */
    public static RobotMode fromNbt(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return IDLE;
        }
    }
}
