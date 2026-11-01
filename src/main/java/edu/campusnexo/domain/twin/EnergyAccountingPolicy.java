package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Energy saved against a conventional baseline: the unit running at full power while the room is in scheduled use
 * (from {@link #PRE_START} before a booking until it ends, or while people are in it), within the campus hours. That is
 * what a timer or a wall switch does in Pasto; CampusNexo saves on it by stopping on the thermal inertia, ventilating
 * instead of heating and locking empty rooms. Savings for an interval = (baseline share − actual share) × rated power ×
 * hours. A unit that is off draws nothing, so it never counts against; running it outside scheduled use (pre-heating
 * earlier than the timer would) does. Gaps between readings are capped so missing data is not extrapolated.
 */
public final class EnergyAccountingPolicy {
    /** How long before a booking a conventional timer starts the unit. */
    public static final Duration PRE_START = Duration.ofMinutes(30);
    private final double ratedKwPerSqm;
    private final Duration maxInterval;
    private final LocalTime scheduleStart;
    private final LocalTime scheduleEnd;
    private final ZoneId zone;

    public EnergyAccountingPolicy(double ratedKwPerSqm, Duration maxInterval, LocalTime scheduleStart,
                                  LocalTime scheduleEnd, ZoneId zone) {
        require(ratedKwPerSqm > 0 && !maxInterval.isNegative() && scheduleStart.isBefore(scheduleEnd),
                "Parámetros de contabilidad energética no válidos");
        this.ratedKwPerSqm = ratedKwPerSqm;
        this.maxInterval = maxInterval;
        this.scheduleStart = scheduleStart;
        this.scheduleEnd = scheduleEnd;
        this.zone = zone;
    }

    public double ratedKw(double areaSqm) {
        return ratedKwPerSqm * areaSqm;
    }

    /** @param scheduledUse the room was in scheduled use during the interval: the conventional baseline runs it */
    public double savedKwh(HvacControlState stateDuringInterval, boolean scheduledUse, Instant from, Instant to,
                           double areaSqm) {
        if (!to.isAfter(from)) {
            return 0;
        }
        Duration interval = Duration.between(from, to);
        if (interval.compareTo(maxInterval) > 0) {
            interval = maxInterval;
        }
        double hours = interval.toMillis() / 3_600_000.0;
        double baselineShare = scheduledUse && inSchedule(from) ? 1.0 : 0.0;
        return (baselineShare - stateDuringInterval.powerFraction()) * ratedKw(areaSqm) * hours;
    }

    private boolean inSchedule(Instant instant) {
        LocalTime local = instant.atZone(zone).toLocalTime();
        return !local.isBefore(scheduleStart) && local.isBefore(scheduleEnd);
    }
}
