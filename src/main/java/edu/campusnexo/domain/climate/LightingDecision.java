package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

public record LightingDecision(int targetLevelPct, boolean closeBlinds, String explanation) {
    public LightingDecision {
        requireRange(targetLevelPct, 0, 100, "lighting.targetLevelPct");
        requireText(explanation, "lighting.explanation");
    }
}
