package edu.campusnexo.domain.twin;

import edu.campusnexo.domain.climate.ComfortBand;
import edu.campusnexo.domain.climate.HvacMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Receding-horizon controller over a first-order (RC) thermal model of the room:
 *
 * <pre>T(k+1) = T_out + (T(k) − T_out)·e^(−Δt/τ) + g·n·(50/A)·Δt/60</pre>
 *
 * where n is the number of occupants, A the floor area and g the heat gain per occupant. At each
 * telemetry frame the free-running trajectory (unit off) is simulated over the horizon and the cheapest
 * strategy that keeps the room inside the comfort band of its climate family is chosen:
 * <ol>
 *   <li>occupied and out of band → {@code CONFORT_ACTIVO}, heating or cooling back into the band;</li>
 *   <li>occupied, in band, CO2 above the limit → {@code VENTILACION_CO2_ACTIVA};</li>
 *   <li>occupied and the trajectory stays in band until the class ends → {@code CORTE_INERCIA_TERMICA};</li>
 *   <li>empty, class soon and the room would arrive too warm or too cold → {@code PRE_ENFRIAMIENTO_IA} or
 *       {@code PRE_CALENTAMIENTO_IA} when the remaining time is just enough to bring it into the band,
 *       otherwise {@code ECO_SETBACK_ENTRE_CLASES};</li>
 *   <li>empty with no class within the setback window → {@code BLOQUEO_AHORRO_VACIO}.</li>
 * </ol>
 * Pure and stateless. Maintenance, the teacher's manual control and compressor protection are applied
 * afterwards by the HVAC rule chain ({@code HvacSettingChain}).
 */
public final class ThermalInertiaMpcDomainService {
    private static final double REFERENCE_AREA_SQM = 50.0;
    private static final double PRE_CONDITIONING_MARGIN_C = 0.5;

    private final ThermalParameters parameters;

    public ThermalInertiaMpcDomainService(ThermalParameters parameters) {
        this.parameters = parameters;
    }

    public ThermalParameters parameters() {
        return parameters;
    }

    public ThermalDecision decide(ThermalContext context) {
        return context.occupied() ? decideOccupied(context) : decideEmpty(context);
    }

    private ThermalDecision decideOccupied(ThermalContext context) {
        ComfortBand band = context.comfortBand();
        double indoor = context.indoorTemperatureC();
        int horizon = context.minutesUntilOccupancyEnds() == null
                ? parameters.defaultOccupiedMinutes()
                : Math.max(parameters.stepMinutes(), context.minutesUntilOccupancyEnds());
        List<Double> trajectory = freeRunning(indoor, context.outdoorTemperatureC(), context.occupants(),
                context.areaSqm(), horizon);
        if (!band.contains(indoor)) {
            return active(HvacControlState.CONFORT_ACTIVO, indoor, backIntoBand(indoor, band), horizon, trajectory,
                    String.format(Locale.ROOT, "Temperatura de %.1f °C fuera de la banda %.1f–%.1f °C con ocupantes",
                            indoor, band.lowC(), band.highC()));
        }
        if (context.co2Ppm() > parameters.co2VentilationPpm()) {
            return idle(HvacControlState.VENTILACION_CO2_ACTIVA, band.midpoint(), horizon, trajectory,
                    String.format(Locale.ROOT, "CO2 de %.0f ppm sobre %.0f ppm: prioridad a la renovación de aire",
                            context.co2Ppm(), parameters.co2VentilationPpm()));
        }
        if (trajectory.stream().allMatch(band::contains)) {
            double max = trajectory.stream().mapToDouble(Double::doubleValue).max().orElse(indoor);
            double min = trajectory.stream().mapToDouble(Double::doubleValue).min().orElse(indoor);
            return idle(HvacControlState.CORTE_INERCIA_TERMICA, indoor, horizon, trajectory, String.format(Locale.ROOT,
                    "La inercia térmica mantiene el confort %d min (previsto %.1f–%.1f °C): equipo apagado",
                    horizon, min, max));
        }
        double exit = firstOutOfBand(trajectory, band);
        return active(HvacControlState.CONFORT_ACTIVO, indoor, backIntoBand(exit, band), horizon, trajectory,
                String.format(Locale.ROOT, "Sin climatizar saldría de la banda (%.1f °C) antes de terminar la ocupación",
                        exit));
    }

    private ThermalDecision decideEmpty(ThermalContext context) {
        ComfortBand band = context.comfortBand();
        double indoor = context.indoorTemperatureC();
        Integer next = context.minutesUntilNextOccupancy();
        if (next == null || next > parameters.ecoSetbackWindowMinutes()) {
            List<Double> trajectory = freeRunning(indoor, context.outdoorTemperatureC(), 0, context.areaSqm(),
                    parameters.defaultOccupiedMinutes());
            return idle(HvacControlState.BLOQUEO_AHORRO_VACIO, indoor, parameters.defaultOccupiedMinutes(), trajectory,
                    "Espacio vacío sin ocupación prevista en la ventana de control: climatización bloqueada");
        }
        int horizon = Math.max(parameters.stepMinutes(), next);
        List<Double> trajectory = freeRunning(indoor, context.outdoorTemperatureC(), 0, context.areaSqm(), horizon);
        double atArrival = trajectory.get(trajectory.size() - 1);
        if (next <= parameters.preConditioningLeadMinutes() && !band.contains(atArrival)) {
            boolean tooWarm = atArrival > band.highC();
            double target = tooWarm ? band.highC() - PRE_CONDITIONING_MARGIN_C : band.lowC() + PRE_CONDITIONING_MARGIN_C;
            double minutesNeeded = Math.abs(atArrival - target) / parameters.hvacRateCPerMinute();
            if (minutesNeeded >= next - parameters.stepMinutes()) {
                HvacControlState state = tooWarm ? HvacControlState.PRE_ENFRIAMIENTO_IA : HvacControlState.PRE_CALENTAMIENTO_IA;
                return active(state, indoor, target, horizon, trajectory, String.format(Locale.ROOT,
                        "Clase en %d min y llegaría a %.1f °C: se %s (%.0f min necesarios)", next, atArrival,
                        tooWarm ? "pre-enfría" : "pre-calienta", Math.ceil(minutesNeeded)));
            }
        }
        double setback = atArrival > band.midpoint() ? band.highC() + parameters.ecoSetbackOffsetC()
                : band.lowC() - parameters.ecoSetbackOffsetC();
        return active(HvacControlState.ECO_SETBACK_ENTRE_CLASES, indoor, setback, horizon, trajectory,
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

    /** A strategy with the unit running: heating or cooling towards {@code target}. */
    private ThermalDecision active(HvacControlState state, double indoorC, double target, int horizon,
                                   List<Double> trajectory, String explanation) {
        double setpoint = round(clampSetpoint(target));
        return new ThermalDecision(state, HvacMode.toward(indoorC, setpoint), setpoint, horizon,
                parameters.stepMinutes(), trajectory, explanation);
    }

    /** A strategy with the unit stopped; the setpoint is informative. */
    private ThermalDecision idle(HvacControlState state, double setpoint, int horizon, List<Double> trajectory,
                                 String explanation) {
        return new ThermalDecision(state, HvacMode.APAGADO, round(clampSetpoint(setpoint)), horizon,
                parameters.stepMinutes(), trajectory, explanation);
    }

    /** One degree inside the nearest edge of the band. */
    private static double backIntoBand(double temperatureC, ComfortBand band) {
        return temperatureC > band.highC() ? band.highC() - 1 : band.lowC() + 1;
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
