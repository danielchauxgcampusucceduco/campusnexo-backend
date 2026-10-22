package edu.campusnexo.application.climate;

import edu.campusnexo.domain.climate.HvacState;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.HvacSetting;
import java.time.Clock;

/**
 * Current HVAC mode of a room and for how long, taken from what the digital twin actually commanded to the
 * room's unit. The compressor-protection decorator of the recommendation therefore protects the real
 * compressor, not a history of recommendations that may never have been applied.
 */
public class HvacStateResolver {
    private final SpaceRepositoryPort twins;
    private final Clock clock;

    public HvacStateResolver(SpaceRepositoryPort twins, Clock clock) {
        this.twins = twins;
        this.clock = clock;
    }

    public HvacState resolve(String spaceId) {
        return twins.findTwin(spaceId).map(twin -> {
            HvacSetting hvac = twin.hvac();
            int minutes = (int) Math.min(hvac.minutesSince(clock.instant()), HvacState.MAX_MINUTES);
            return new HvacState(hvac.mode(), minutes);
        }).orElseGet(HvacState::initial);
    }
}
