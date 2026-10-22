package edu.campusnexo.domain.twin.hvac;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.policy.ClimatePolicyFactory;
import edu.campusnexo.domain.twin.HvacSetting;
import edu.campusnexo.domain.twin.ThermalDecision;
import java.time.Instant;

/**
 * Everything the HVAC rules may look at to decide what a room's unit does now.
 *
 * @param previous      the setting the unit has now
 * @param predicted     what the predictive controller proposes
 * @param family        climate family of the room: its thermal strategy and compressor protection
 * @param climate       room conditions in the shape the family's strategies read
 * @param firstDecision {@code true} for a room's first frame: there is no real previous run to protect
 */
public record HvacRequest(
        boolean hvacInstalled,
        boolean maintenance,
        boolean overrideActive,
        HvacSetting previous,
        ThermalDecision predicted,
        ClimatePolicyFactory family,
        ClimateContext climate,
        boolean firstDecision,
        Instant now) {

    public HvacRequest {
        requireNonNull(previous, "previous");
        requireNonNull(predicted, "predicted");
        requireNonNull(family, "family");
        requireNonNull(climate, "climate");
        requireNonNull(now, "now");
    }
}
