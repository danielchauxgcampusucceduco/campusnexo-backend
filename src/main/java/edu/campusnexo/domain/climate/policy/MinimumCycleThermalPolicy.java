package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.HvacState;

/**
 * Decorator over any {@link ThermalPolicy}: adds compressor protection (minimum on and off times)
 * without changing the wrapped algorithm. Every climate family reuses it.
 */
public final class MinimumCycleThermalPolicy implements ThermalPolicy {
    private final ThermalPolicy delegate;
    private final int minimumOnMinutes;
    private final int minimumOffMinutes;

    public MinimumCycleThermalPolicy(ThermalPolicy delegate, int minimumOnMinutes, int minimumOffMinutes) {
        this.delegate = delegate;
        this.minimumOnMinutes = minimumOnMinutes;
        this.minimumOffMinutes = minimumOffMinutes;
    }

    public ThermalPolicy delegate() {
        return delegate;
    }

    @Override
    public HvacDecision recommend(ClimateContext context) {
        HvacDecision desired = delegate.recommend(context);
        HvacState current = context.currentHvac();
        boolean switchingOff = current.mode().isActive() && !desired.enabled();
        boolean switchingOn = !current.mode().isActive() && desired.enabled();
        if (switchingOff && current.minutesInMode() < minimumOnMinutes) {
            return new HvacDecision(current.mode(), desired.setpointC(),
                    "Se mantiene " + current.mode() + " para respetar el tiempo mínimo encendido del compresor ("
                            + minimumOnMinutes + " min). Recomendación pendiente: " + desired.explanation());
        }
        if (switchingOn && current.minutesInMode() < minimumOffMinutes) {
            return new HvacDecision(HvacMode.APAGADO, desired.setpointC(),
                    "Se mantiene apagado para respetar el tiempo mínimo de reposo del compresor ("
                            + minimumOffMinutes + " min). Recomendación pendiente: " + desired.explanation());
        }
        return desired;
    }
}
