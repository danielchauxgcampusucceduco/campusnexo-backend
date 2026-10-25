package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Energy saved against a conventional baseline: the unit running at full power during the campus
 * operating schedule. Savings for an interval = (baseline share − actual share) × rated power × hours;
 * outside the schedule the baseline is off, so running the unit counts as negative savings. Gaps
 * between readings are capped so missing data is not extrapolated.
 */
public final class EnergyAccountingPolicy {
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

    public double savedKwh(HvacControlState stateDuringInterval, Instant from, Instant to, double areaSqm) {
        if (!to.isAfter(from)) {
            return 0;
        }
        Duration interval = Duration.between(from, to);
        if (interval.compareTo(maxInterval) > 0) {
            interval = maxInterval;
        }
        double hours = interval.toMillis() / 3_600_000.0;
        double baselineShare = inSchedule(from) ? 1.0 : 0.0;
        return (baselineShare - stateDuringInterval.powerFraction()) * ratedKw(areaSqm) * hours;
    }

    private boolean inSchedule(Instant instant) {
        LocalTime local = instant.atZone(zone).toLocalTime();
        return !local.isBefore(scheduleStart) && local.isBefore(scheduleEnd);
    }
}
