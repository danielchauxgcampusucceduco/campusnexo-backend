package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;

import java.time.Instant;

/** Gaussian belief about the number of occupants: mean {@code estimate} and {@code variance}. */
public record OccupancyBelief(double estimate, double variance, Instant at) {
    public OccupancyBelief {
        require(Double.isFinite(estimate) && estimate >= 0, "La ocupación estimada no puede ser negativa");
        require(Double.isFinite(variance) && variance > 0, "La varianza debe ser positiva");
        requireNonNull(at, "at");
    }

    /** Uninformative prior: empty room with a large uncertainty. */
    public static OccupancyBelief uninformed(Instant at, int capacity) {
        return new OccupancyBelief(0, Math.max(4, capacity * capacity / 4.0), at);
    }

    public int people() {
        return (int) Math.round(estimate);
    }

    public double standardDeviation() {
        return Math.sqrt(variance);
    }
}
