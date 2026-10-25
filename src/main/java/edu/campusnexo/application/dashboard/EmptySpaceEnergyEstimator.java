package edu.campusnexo.application.dashboard;

import edu.campusnexo.domain.telemetry.Telemetry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Estimates energy consumed while spaces were empty: each empty reading's power is integrated
 * until the next reading of the same space, with gaps capped so missing data is not extrapolated.
 */
public class EmptySpaceEnergyEstimator {
    private final Duration maxInterval;

    public EmptySpaceEnergyEstimator(Duration maxInterval) {
        this.maxInterval = maxInterval;
    }

    public Result estimate(List<Telemetry> readingsOrderedBySpaceAndTime, Instant now,
                           ToDoubleFunction<String> tariffCopPerKwh) {
        double kwh = 0;
        double cost = 0;
        for (int i = 0; i < readingsOrderedBySpaceAndTime.size(); i++) {
            Telemetry reading = readingsOrderedBySpaceAndTime.get(i);
            if (!reading.isEmpty()) {
                continue;
            }
            Instant end = now;
            if (i + 1 < readingsOrderedBySpaceAndTime.size()
                    && readingsOrderedBySpaceAndTime.get(i + 1).spaceId().equals(reading.spaceId())) {
                end = readingsOrderedBySpaceAndTime.get(i + 1).recordedAt();
            }
            Duration interval = Duration.between(reading.recordedAt(), end);
            if (interval.isNegative()) {
                continue;
            }
            if (interval.compareTo(maxInterval) > 0) {
                interval = maxInterval;
            }
            double readingKwh = reading.activePowerKw() * interval.toSeconds() / 3600.0;
            kwh += readingKwh;
            cost += readingKwh * tariffCopPerKwh.applyAsDouble(reading.spaceId());
        }
        return new Result(round(kwh, 3), round(cost, 0));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    public record Result(double kwh, double costCop) {
    }

    /** Helper so callers can build the tariff lookup from the catalog. */
    public static ToDoubleFunction<String> tariffLookup(Map<String, Double> tariffBySpace) {
        return spaceId -> tariffBySpace.getOrDefault(spaceId, 0.0);
    }
}
