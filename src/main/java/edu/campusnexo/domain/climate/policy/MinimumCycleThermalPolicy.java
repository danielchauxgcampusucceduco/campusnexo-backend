package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.CompressorGuard;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;
import edu.campusnexo.domain.climate.HvacState;

/**
 * Decorator over any {@link ThermalPolicy}: adds compressor protection (minimum on and off times)
 * without changing the wrapped algorithm. Every climate family reuses it; the rule itself lives in
 * {@link CompressorGuard}, which the digital twin applies to its own decisions as well.
 */
public final class MinimumCycleThermalPolicy implements ThermalPolicy {
    private final ThermalPolicy delegate;
    private final CompressorGuard guard;

    public MinimumCycleThermalPolicy(ThermalPolicy delegate, CompressorGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    public MinimumCycleThermalPolicy(ThermalPolicy delegate, int minimumOnMinutes, int minimumOffMinutes) {
        this(delegate, new CompressorGuard(minimumOnMinutes, minimumOffMinutes));
    }

    public ThermalPolicy delegate() {
        return delegate;
    }

    public CompressorGuard guard() {
        return guard;
    }

    @Override
    public HvacDecision recommend(ClimateContext context) {
        HvacDecision desired = delegate.recommend(context);
        HvacState current = context.currentHvac();
        boolean onNow = current.mode().isActive();
        if (!guard.mustHold(onNow, desired.enabled(), current.minutesInMode())) {
            return desired;
        }
        if (onNow) {
            return new HvacDecision(current.mode(), desired.setpointC(),
                    "Se mantiene " + current.mode() + " para respetar el tiempo mínimo encendido del compresor ("
                            + guard.minimumOnMinutes() + " min). Recomendación pendiente: " + desired.explanation());
        }
        return new HvacDecision(HvacMode.APAGADO, desired.setpointC(),
                "Se mantiene apagado para respetar el tiempo mínimo de reposo del compresor ("
                        + guard.minimumOffMinutes() + " min). Recomendación pendiente: " + desired.explanation());
    }
}
