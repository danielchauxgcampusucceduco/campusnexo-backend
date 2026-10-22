package edu.campusnexo.domain.twin.hvac;

import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.twin.HvacControlState;

/** A room without a climate unit has nothing to command. */
final class NoClimateUnitRule implements HvacSettingRule {
    @Override
    public HvacResolution resolve(HvacRequest request, HvacSettingChain next) {
        if (request.hvacInstalled()) {
            return next.proceed(request);
        }
        return new HvacResolution(HvacControlState.SIN_CLIMATIZACION, HvacMode.APAGADO, request.previous().setpointC(),
                "El espacio no tiene equipo de climatización", ruleName());
    }
}
