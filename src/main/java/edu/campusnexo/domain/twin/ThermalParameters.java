package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;

/**
 * Parameters of the first-order thermal model and of the control horizon (illustrative values).
 *
 * @param timeConstantMinutes        τ of the room: how fast it drifts towards the outdoor temperature
 * @param occupantGainCPerHour       temperature rise per occupant per hour in a 50 m² reference room
 * @param hvacRateCPerMinute         how fast the unit moves the temperature when it is running
 * @param stepMinutes                simulation step
 * @param defaultOccupiedMinutes     horizon used when an occupied room has no reservation end
 * @param preConditioningLeadMinutes look-ahead window to start pre-cooling before a class
 * @param ecoSetbackWindowMinutes    gap between classes in which the setback is preferred to a full stop
 * @param ecoSetbackOffsetC          how far the setpoint is relaxed during the setback
 * @param co2VentilationPpm          CO2 level above which ventilation has priority
 */
public record ThermalParameters(
        double timeConstantMinutes,
        double occupantGainCPerHour,
        double hvacRateCPerMinute,
        int stepMinutes,
        int defaultOccupiedMinutes,
        int preConditioningLeadMinutes,
        int ecoSetbackWindowMinutes,
        double ecoSetbackOffsetC,
        double co2VentilationPpm) {

    public ThermalParameters {
        require(timeConstantMinutes > 0 && occupantGainCPerHour >= 0 && hvacRateCPerMinute > 0 && stepMinutes > 0
                && defaultOccupiedMinutes > 0 && preConditioningLeadMinutes > 0 && ecoSetbackWindowMinutes > 0
                && ecoSetbackOffsetC >= 0 && co2VentilationPpm > 0, "Parámetros térmicos no válidos");
    }

    public static ThermalParameters defaults() {
        return new ThermalParameters(120, 0.06, 0.08, 5, 45, 30, 60, 2.0, 1000);
    }
}
