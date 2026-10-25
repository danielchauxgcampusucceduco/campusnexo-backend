package edu.campusnexo.domain.twin.hvac;

import java.util.List;

/**
 * The ordered HVAC rules of a room. Order expresses precedence: a room without a unit is never climatized,
 * maintenance stops the unit, the teacher's control beats automation, compressor protection can delay what
 * the automation asks for, the climate family chooses the mode, and the predictive controller proposes the
 * strategy at the end of the chain.
 */
public final class HvacSettingChain {
    private final List<HvacSettingRule> rules;
    private final int position;

    public HvacSettingChain(List<HvacSettingRule> rules) {
        this(List.copyOf(rules), 0);
        if (rules.isEmpty()) {
            throw new IllegalArgumentException("La cadena de reglas HVAC no puede estar vacía");
        }
    }

    private HvacSettingChain(List<HvacSettingRule> rules, int position) {
        this.rules = rules;
        this.position = position;
    }

    public static HvacSettingChain standard() {
        return new HvacSettingChain(List.of(new NoClimateUnitRule(), new MaintenanceRule(), new ManualOverrideRule(),
                new CompressorProtectionRule(), new FamilyStrategyRule(), new PredictiveControlRule()));
    }

    public HvacResolution proceed(HvacRequest request) {
        if (position >= rules.size()) {
            throw new IllegalStateException("Ninguna regla HVAC decidió: la última regla debe ser terminal");
        }
        return rules.get(position).resolve(request, new HvacSettingChain(rules, position + 1));
    }

    public List<String> ruleNames() {
        return rules.stream().map(HvacSettingRule::ruleName).toList();
    }
}
