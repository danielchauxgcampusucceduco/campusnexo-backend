package edu.campusnexo.domain.vision;

import static edu.campusnexo.domain.shared.DomainChecks.requireRange;

/** Point in image coordinates normalized to [0, 1], independent of the camera resolution. */
public record NormalizedPoint(double x, double y) {
    public NormalizedPoint {
        requireRange(x, 0, 1, "x");
        requireRange(y, 0, 1, "y");
    }
}
