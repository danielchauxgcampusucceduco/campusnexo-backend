package edu.campusnexo.domain.policy;

public record VentilationDecision(boolean enabled, String explanation) {
    public VentilationDecision { if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("Falta explicación de ventilación"); }
}
