package edu.campusnexo.domain.twin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Receding-horizon controller over a first-order (RC) thermal model of the room:
 *
 * <pre>T(k+1) = T_out + (T(k) − T_out)·e^(−Δt/τ) + g·n·(50/A)·Δt/60</pre>
 *
 * where n is the number of occupants, A the floor area and g the heat gain per occupant. At each
 * telemetry frame the free-running trajectory (compressor off) is simulated over the horizon and the
 * cheapest strategy that keeps the room inside the comfort band is chosen:
 * <ol>
 *   <li>occupied and out of band → {@code CONFORT_ACTIVO};</li>
 *   <li>occupied, in band, CO2 above the limit → {@code VENTILACION_CO2_ACTIVA};</li>
 *   <li>occupied and the trajectory stays in band until the class ends → {@code CORTE_INERCIA_TERMICA};</li>
 *   <li>empty, class soon and the room would start too warm → {@code PRE_ENFRIAMIENTO_IA} when the remaining
 *       time is just enough to cool it, otherwise {@code ECO_SETBACK_ENTRE_CLASES};</li>
 *   <li>empty with no class within the setback window → {@code BLOQUEO_AHORRO_VACIO}.</li>
 * </ol>
 * Pure and stateless; the manual override is handled by the use case before calling this service.
 */
public final class ThermalInertiaMpcDomainService {
    private static final double REFERENCE_AREA_SQM = 50.0;
    private static final double PRE_COOLING_MARGIN_C = 0.5;

    private final ThermalParameters parameters;

    public ThermalInertiaMpcDomainService(ThermalParameters parameters) {
        this.parameters = parameters;
    }

    public ThermalParameters parameters() {
        return parameters;
    }

    public ThermalDecision decide(ThermalContext context) {
        ComfortBand band = context.comfortBand();
        double indoor = context.indoorTemperatureC();

        if (context.occupied()) {
            int horizon = context.minutesUntilOccupancyEnds() == null
                    ? parameters.defaultOccupiedMinutes()
                    : Math.max(parameters.stepMinutes(), context.minutesUntilOccupancyEnds());
            List<Double> trajectory = freeRunning(indoor, context.outdoorTemperatureC(), context.occupants(),
                    context.areaSqm(), horizon);
            if (!band.contains(indoor)) {
                boolean tooWarm = indoor > band.highC();
                return decision(HvacControlState.CONFORT_ACTIVO, tooWarm ? band.highC() - 1 : band.lowC() + 1, horizon,
                        trajectory, String.format(Locale.ROOT, "Temperatura de %.1f °C fuera de la banda %.1f–%.1f °C con ocupantes",
                                indoor, band.lowC(), band.highC()));
            }
            if (context.co2Ppm() > parameters.co2VentilationPpm()) {
                return decision(HvacControlState.VENTILACION_CO2_ACTIVA, band.midpoint(), horizon, trajectory,
                        String.format(Locale.ROOT, "CO2 de %.0f ppm sobre %.0f ppm: prioridad a la renovación de aire",
                                context.co2Ppm(), parameters.co2VentilationPpm()));
            }
            if (trajectory.stream().allMatch(band::contains)) {
                double max = trajectory.stream().mapToDouble(Double::doubleValue).max().orElse(indoor);
                return decision(HvacControlState.CORTE_INERCIA_TERMICA, band.highC(), horizon, trajectory,
                        String.format(Locale.ROOT, "La inercia térmica mantiene el confort %d min (máx. previsto %.1f °C): compresor apagado",
                                horizon, max));
            }
            double exit = firstOutOfBand(trajectory, band);
            return decision(HvacControlState.CONFORT_ACTIVO, exit > band.highC() ? band.highC() - 1 : band.lowC() + 1,
                    horizon, trajectory, String.format(Locale.ROOT,
                            "Sin climatizar saldría de la banda (%.1f °C) antes de terminar la ocupación", exit));
        }

        Integer next = context.minutesUntilNextOccupancy();
        if (next == null || next > parameters.ecoSetbackWindowMinutes()) {
            List<Double> trajectory = freeRunning(indoor, context.outdoorTemperatureC(), 0, context.areaSqm(),
                    parameters.defaultOccupiedMinutes());
            return decision(HvacControlState.BLOQUEO_AHORRO_VACIO, band.midpoint(), parameters.defaultOccupiedMinutes(),
                    trajectory, "Espacio vacío sin ocupación prevista en la ventana de control: climatización bloqueada");
        }
        int horizon = Math.max(parameters.stepMinutes(), next);
        List<Double> trajectory = freeRunning(indoor, context.outdoorTemperatureC(), 0, context.areaSqm(), horizon);
        double atArrival = trajectory.get(trajectory.size() - 1);
        if (next <= parameters.preConditioningLeadMinutes() && atArrival > band.highC()) {
            double target = band.highC() - PRE_COOLING_MARGIN_C;
            double minutesNeeded = (atArrival - target) / parameters.hvacRateCPerMinute();
            if (minutesNeeded >= next - parameters.stepMinutes()) {
                return decision(HvacControlState.PRE_ENFRIAMIENTO_IA, target, horizon, trajectory,
                        String.format(Locale.ROOT, "Clase en %d min y llegaría a %.1f °C: se pre-enfría (%.0f min necesarios)",
                                next, atArrival, Math.ceil(minutesNeeded)));
            }
        }
        double setback = atArrival > band.midpoint() ? band.highC() + parameters.ecoSetbackOffsetC()
                : band.lowC() - parameters.ecoSetbackOffsetC();
        return decision(HvacControlState.ECO_SETBACK_ENTRE_CLASES, clampSetpoint(setback), horizon, trajectory,
                String.format(Locale.ROOT, "Clase en %d min: setpoint relajado %.1f °C entre clases", next,
                        parameters.ecoSetbackOffsetC()));
    }

    /** Free-running (compressor off) trajectory, one value per step including the starting point. */
    public List<Double> freeRunning(double indoorC, double outdoorC, int occupants, double areaSqm, int horizonMinutes) {
        int steps = Math.max(1, (int) Math.ceil(horizonMinutes / (double) parameters.stepMinutes()));
        double decay = Math.exp(-parameters.stepMinutes() / parameters.timeConstantMinutes());
        double gainPerStep = parameters.occupantGainCPerHour() * occupants * (REFERENCE_AREA_SQM / areaSqm)
                * parameters.stepMinutes() / 60.0;
        List<Double> trajectory = new ArrayList<>(steps + 1);
        double temperature = indoorC;
        trajectory.add(round(temperature));
        for (int k = 0; k < steps; k++) {
            temperature = outdoorC + (temperature - outdoorC) * decay + gainPerStep;
            trajectory.add(round(temperature));
        }
        return trajectory;
    }

    private ThermalDecision decision(HvacControlState state, double setpoint, int horizon, List<Double> trajectory,
                                     String explanation) {
        return new ThermalDecision(state, round(clampSetpoint(setpoint)), horizon, parameters.stepMinutes(),
                trajectory, explanation);
    }

    private static double firstOutOfBand(List<Double> trajectory, ComfortBand band) {
        return trajectory.stream().filter(t -> !band.contains(t)).findFirst().orElse(trajectory.get(trajectory.size() - 1));
    }

    private static double clampSetpoint(double setpoint) {
        return Math.min(30, Math.max(16, setpoint));
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
