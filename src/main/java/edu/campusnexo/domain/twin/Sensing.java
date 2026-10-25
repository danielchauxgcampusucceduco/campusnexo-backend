package edu.campusnexo.domain.twin;

import java.time.Instant;

/**
 * What the room's devices last reported: whether the camera is online and the capture time of the last
 * camera frame and of the last Arduino reading. Readings are applied in capture order: a reading that is
 * not newer than the last one of its device is a duplicate (an MQTT redelivery) or arrived late, and is
 * ignored, which makes the twin idempotent.
 *
 * <p>{@code emulated} says the last camera frame did not come from a real camera, so
 * every screen can say so; the twin treats both the same way.
 */
public record Sensing(boolean cameraOnline, Instant lastCameraFrameAt, Instant lastEnvironmentAt, boolean emulated) {
    public static Sensing none() {
        return new Sensing(false, null, null, false);
    }

    public boolean acceptsCameraFrame(Instant capturedAt) {
        return lastCameraFrameAt == null || capturedAt.isAfter(lastCameraFrameAt);
    }

    public boolean acceptsEnvironmentReading(Instant capturedAt) {
        return lastEnvironmentAt == null || capturedAt.isAfter(lastEnvironmentAt);
    }

    public Sensing cameraFrame(boolean online, Instant capturedAt, boolean emulatedFrame) {
        return new Sensing(online, capturedAt, lastEnvironmentAt, emulatedFrame);
    }

    public Sensing environmentReading(Instant capturedAt) {
        return new Sensing(cameraOnline, lastCameraFrameAt, capturedAt, emulated);
    }
}
