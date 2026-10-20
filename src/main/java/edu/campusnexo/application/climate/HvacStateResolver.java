package edu.campusnexo.application.climate;

import edu.campusnexo.domain.climate.ClimateRecommendation;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.port.ClimateRecommendationRepository;
import edu.campusnexo.domain.shared.ResultLimit;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The prototype does not read real HVAC equipment, so the current state is assumed to be the
 * last recommended mode, active since the first recommendation of the current run of that mode.
 */
public class HvacStateResolver {
    private final ClimateRecommendationRepository recommendations;
    private final Clock clock;

    public HvacStateResolver(ClimateRecommendationRepository recommendations, Clock clock) {
        this.recommendations = recommendations;
        this.clock = clock;
    }

    public HvacState resolve(String spaceId) {
        List<ClimateRecommendation> history = recommendations.findRecent(spaceId, ResultLimit.of(ResultLimit.MAX));
        if (history.isEmpty()) {
            return HvacState.initial();
        }
        HvacMode mode = history.get(0).hvac().mode();
        Instant since = history.get(0).generatedAt();
        for (ClimateRecommendation previous : history) {
            if (previous.hvac().mode() != mode) {
                break;
            }
            since = previous.generatedAt();
        }
        long minutes = Math.max(0, Duration.between(since, clock.instant()).toMinutes());
        return new HvacState(mode, (int) Math.min(minutes, HvacState.MAX_MINUTES));
    }
}
