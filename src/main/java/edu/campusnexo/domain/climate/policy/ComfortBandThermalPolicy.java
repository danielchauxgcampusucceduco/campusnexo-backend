package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;

/**
 * Mild climate: HVAC only outside a wide comfort band. When it is warm inside but cooler outside,
 * the policy prefers free cooling (natural ventilation) over running the compressor.
 */
public final class ComfortBandThermalPolicy implements ThermalPolicy {
    private final double lowerC;
    private final double upperC;
    private final double freeCoolingMarginC;

    public ComfortBandThermalPolicy(double lowerC, double upperC, double freeCoolingMarginC) {
        this.lowerC = lowerC;
        this.upperC = upperC;
        this.freeCoolingMarginC = freeCoolingMarginC;
    }

    @Override
    public HvacDecision recommend(ClimateContext context) {
        double indoor = context.indoorTemperatureC();
        if (!context.occupied()) {
            return new HvacDecision(HvacMode.APAGADO, upperC, "Sin ocupación; no se climatiza el espacio");
        }
        if (indoor < lowerC) {
            return new HvacDecision(HvacMode.CALEFACCION, lowerC, "Temperatura interior bajo la banda de confort");
        }
        if (indoor > upperC) {
            if (context.outsideTemperatureC() <= indoor - freeCoolingMarginC) {
                return new HvacDecision(HvacMode.APAGADO, upperC,
                        "Exterior más fresco que el interior; se prefiere enfriamiento libre por ventilación natural");
            }
            return new HvacDecision(HvacMode.ENFRIAMIENTO, upperC, "Temperatura interior sobre la banda de confort");
        }
        return new HvacDecision(HvacMode.APAGADO, upperC, "Temperatura interior dentro de la banda de confort");
    }
}
