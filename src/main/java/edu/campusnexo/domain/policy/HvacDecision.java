package edu.campusnexo.domain.policy;

public record HvacDecision(boolean enabled, int setpointC, String explanation) {
    public HvacDecision {
        if (setpointC < 16 || setpointC > 30) throw new IllegalArgumentException("Setpoint HVAC debe estar entre 16 y 30 °C");
        if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("La decisión HVAC debe explicar su motivo");
    }
}
