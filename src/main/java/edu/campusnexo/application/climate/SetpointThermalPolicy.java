package edu.campusnexo.application.climate;

import edu.campusnexo.domain.policy.ClimateContext;
import edu.campusnexo.domain.policy.HvacDecision;
import edu.campusnexo.domain.policy.ThermalPolicy;

/** Strategy applying profile-specific setpoints and the compressor's minimum cycle times. */
final class SetpointThermalPolicy implements ThermalPolicy {
    private static final int MINIMUM_ON_MINUTES = 10;
    private static final int MINIMUM_OFF_MINUTES = 5;
    private final double setpointC;
    SetpointThermalPolicy(double setpointC) { this.setpointC = setpointC; }
    @Override public HvacDecision recommend(ClimateContext context) {
        boolean desiredOn = context.occupancy() > 0 && context.indoorTemperatureC() > setpointC;
        boolean blockedByMinimumCycle = context.hvacOn() != desiredOn
                && context.minutesInCurrentHvacState() < (context.hvacOn() ? MINIMUM_ON_MINUTES : MINIMUM_OFF_MINUTES);
        boolean enabled = blockedByMinimumCycle ? context.hvacOn() : desiredOn;
        String reason = blockedByMinimumCycle
                ? "Se conserva el estado actual para respetar el ciclo mínimo del compresor"
                : desiredOn ? "Temperatura superior al setpoint del perfil con ocupación presente"
                : context.occupancy() == 0 ? "Sin ocupación; se recomienda HVAC apagado cuando el ciclo lo permita"
                : "La temperatura está dentro del objetivo térmico del perfil";
        return new HvacDecision(enabled, (int) Math.round(setpointC), reason);
    }
}
