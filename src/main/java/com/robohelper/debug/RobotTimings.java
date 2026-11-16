package com.robohelper.debug;

/** Необязательные замеры полного серверного тика робота; включение: -Drobohelper.profile=true. */
public final class RobotTimings {
    public static final boolean ENABLED = Boolean.getBoolean("robohelper.profile");
    private static long nanos;
    private static long maxNanos;
    private static int count;

    private RobotTimings() { }

    /** Вызывается только серверным потоком, когда профилирование включено. */
    public static void record(long elapsed) {
        nanos += elapsed;
        maxNanos = Math.max(maxNanos, elapsed);
        count++;
    }

    /** Забирает накопленные замеры и начинает следующий интервал. Только серверный поток. */
    public static Sample drain() {
        Sample sample = new Sample(count, nanos, maxNanos);
        count = 0;
        nanos = 0;
        maxNanos = 0;
        return sample;
    }

    /** Сумма включает обычный MobEntity.tick, навигацию и все цели установленных модулей. */
    public record Sample(int robots, long totalNanos, long maxRobotNanos) { }
}
