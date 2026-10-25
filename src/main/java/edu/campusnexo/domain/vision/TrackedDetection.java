package edu.campusnexo.domain.vision;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;

/** A detection with its ByteTrack identifier, so the same object keeps its id across frames. */
public record TrackedDetection(long trackId, DetectedObjectClass objectClass, double confidence, BoundingBox box) {
    public TrackedDetection {
        require(trackId >= 0, "trackId no puede ser negativo");
        requireNonNull(objectClass, "objectClass");
        requireRange(confidence, 0, 1, "confidence");
        requireNonNull(box, "box");
    }
}
