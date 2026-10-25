package edu.campusnexo.domain.twin.hvac;

import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.twin.HvacControlState;

/** A room in maintenance has its unit off, immediately: safety before compressor protection. */
final class MaintenanceRule implements HvacSettingRule {
    @Override
    public HvacResolution resolve(HvacRequest request, HvacSettingChain next) {
        if (!request.maintenance()) {
            return next.proceed(request);
        }
        return new HvacResolution(HvacControlState.APAGADO, HvacMode.APAGADO, request.previous().setpointC(),
                "Espacio en mantenimiento: climatización apagada", ruleName());
    }
}
