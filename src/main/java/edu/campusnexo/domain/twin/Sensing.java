package edu.campusnexo.domain.twin;

import java.time.Duration;
import java.time.Instant;

/**
 * What the room's camera last reported: whether it is online and the capture time of its last frame. Frames are
 * applied in capture order: a frame that is not newer than the last one is a duplicate (an MQTT redelivery) or arrived
 * late, and is ignored, which makes the twin idempotent.
 *
 * <p>{@code emulated} says the last frame did not come from a real camera, so every screen can say so; the twin
 * treats both the same way.
 */
public record Sensing(boolean cameraOnline, Instant lastCameraFrameAt, boolean emulated) {
    /** Frames arrive every few seconds; this long without one means the camera or its link is down. */
    public static final Duration CAMERA_SILENCE = Duration.ofMinutes(2);

    public static Sensing none() {
        return new Sensing(false, null, false);
    }

    public boolean acceptsCameraFrame(Instant capturedAt) {
        return lastCameraFrameAt == null || capturedAt.isAfter(lastCameraFrameAt);
    }

    public Sensing cameraFrame(boolean online, Instant capturedAt, boolean emulatedFrame) {
        return new Sensing(online, capturedAt, emulatedFrame);
    }

    /** The camera says it is online but has sent nothing for {@link #CAMERA_SILENCE}. */
    public boolean cameraSilent(Instant now) {
        return cameraOnline && lastCameraFrameAt != null && lastCameraFrameAt.isBefore(now.minus(CAMERA_SILENCE));
    }

    /** A silent camera counts as offline until its next frame. */
    public Sensing cameraLost() {
        return new Sensing(false, lastCameraFrameAt, emulated);
    }

    /** The camera has reported at least once. */
    public boolean reporting() {
        return lastCameraFrameAt != null;
    }
}
