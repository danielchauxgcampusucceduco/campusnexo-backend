package edu.campusnexo.application.occupancy;

import java.time.Duration;

/** Waits between attempts; replaced by a recorder in tests so retries run instantly. */
@FunctionalInterface
public interface Sleeper {
    void sleep(Duration duration);

    static Sleeper threadSleep() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };
    }
}
