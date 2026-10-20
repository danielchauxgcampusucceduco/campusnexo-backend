package edu.campusnexo.domain.vision;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of evaluating one desk. {@code unattendedSince} is when belongings were first seen without
 * a person (start of the abandonment timer); {@code null} when the timer is not running.
 */
public record DeskObservation(
        String zoneId,
        ZoneDeskState state,
        int peopleInside,
        int belongingsInside,
        Instant unattendedSince,
        Instant evaluatedAt) {

    public DeskObservation {
        requireText(zoneId, "zoneId");
        requireNonNull(state, "state");
        requireNonNull(evaluatedAt, "evaluatedAt");
        require(peopleInside >= 0 && belongingsInside >= 0, "Los conteos del puesto no pueden ser negativos");
    }

    /** Minutes the belongings have been unattended at evaluation time (0 when the timer is not running). */
    public long unattendedMinutes() {
        return unattendedSince == null ? 0 : Duration.between(unattendedSince, evaluatedAt).toMinutes();
    }
}
