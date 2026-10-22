package edu.campusnexo.domain.twin.hvac;

import edu.campusnexo.domain.climate.ComfortBand;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.twin.HvacControlState;
import java.util.Locale;

/** While the teacher's manual control is active the unit runs towards the middle of the comfort band. */
final class ManualOverrideRule implements HvacSettingRule {
    @Override
    public HvacResolution resolve(HvacRequest request, HvacSettingChain next) {
        if (!request.overrideActive()) {
            return next.proceed(request);
        }
        ComfortBand band = request.family().comfortBand();
        double target = band.midpoint();
        double indoor = request.climate().indoorTemperatureC();
        return new HvacResolution(HvacControlState.OVERRIDE_MANUAL_DOCENTE, HvacMode.toward(indoor, target), target,
                String.format(Locale.ROOT, "Control manual del docente: hacia %.1f °C", target), ruleName());
    }
}
