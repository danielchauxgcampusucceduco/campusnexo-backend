package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

public record VentilationDecision(VentilationMode mode, String explanation) {
    public VentilationDecision {
        requireNonNull(mode, "ventilation.mode");
        requireText(explanation, "ventilation.explanation");
    }

    public boolean enabled() {
        return mode.isActive();
    }
}
