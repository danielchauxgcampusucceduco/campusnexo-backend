package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.VentilationDecision;
import edu.campusnexo.domain.climate.VentilationMode;

/**
 * Mild climate: opens windows when air must be renewed (high CO2) or when outside air can cool
 * the room, as long as the outside temperature is comfortable; otherwise falls back to fans.
 */
public final class NaturalVentilationPolicy implements VentilationPolicy {
    private final double co2ThresholdPpm;
    private final double minOutsideC;
    private final double maxOutsideC;
    private final double freeCoolingIndoorC;

    public NaturalVentilationPolicy(double co2ThresholdPpm, double minOutsideC, double maxOutsideC,
                                    double freeCoolingIndoorC) {
        this.co2ThresholdPpm = co2ThresholdPpm;
        this.minOutsideC = minOutsideC;
        this.maxOutsideC = maxOutsideC;
        this.freeCoolingIndoorC = freeCoolingIndoorC;
    }

    @Override
    public VentilationDecision recommend(ClimateContext context) {
        boolean co2High = context.co2Ppm() > co2ThresholdPpm;
        boolean freeCooling = context.occupied() && context.indoorTemperatureC() > freeCoolingIndoorC
                && context.outsideTemperatureC() < context.indoorTemperatureC();
        if (!co2High && !freeCooling) {
            return new VentilationDecision(VentilationMode.NINGUNA, "CO2 y temperatura no requieren renovación de aire");
        }
        String reason = co2High ? "CO2 sobre " + (int) co2ThresholdPpm + " ppm" : "el exterior puede enfriar el espacio";
        double outside = context.outsideTemperatureC();
        if (outside >= minOutsideC && outside <= maxOutsideC) {
            return new VentilationDecision(VentilationMode.NATURAL, "Abrir ventanas: " + reason);
        }
        return new VentilationDecision(VentilationMode.MECANICA,
                "Ventilación mecánica: " + reason + ", pero la temperatura exterior no es apta para abrir ventanas");
    }
}
