package edu.campusnexo.domain.policy;

public record LightingDecision(int targetLevelPct, String explanation) {
    public LightingDecision {
        if (targetLevelPct < 0 || targetLevelPct > 100) throw new IllegalArgumentException("Nivel de iluminación debe estar entre 0 y 100 %");
        if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("Falta explicación de iluminación");
    }
}
