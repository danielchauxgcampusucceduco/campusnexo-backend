package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;

/** Hot humid climate: cooling first; when temperature is fine but humidity is high, dehumidify. */
public final class CoolingWithDehumidificationPolicy implements ThermalPolicy {
    private final double coolingSetpointC;
    private final double maxHumidityPct;
    private final double deadbandC;

    public CoolingWithDehumidificationPolicy(double coolingSetpointC, double maxHumidityPct, double deadbandC) {
        this.coolingSetpointC = coolingSetpointC;
        this.maxHumidityPct = maxHumidityPct;
        this.deadbandC = deadbandC;
    }

    @Override
    public HvacDecision recommend(ClimateContext context) {
        if (!context.occupied()) {
            return new HvacDecision(HvacMode.APAGADO, coolingSetpointC, "Sin ocupación; no se climatiza el espacio");
        }
        if (context.indoorTemperatureC() > coolingSetpointC + deadbandC) {
            return new HvacDecision(HvacMode.ENFRIAMIENTO, coolingSetpointC,
                    "Temperatura interior sobre el setpoint de enfriamiento con ocupación presente");
        }
        if (context.humidityPct() > maxHumidityPct) {
            return new HvacDecision(HvacMode.DESHUMIDIFICACION, coolingSetpointC,
                    "Humedad relativa sobre el máximo de confort; se recomienda deshumidificar");
        }
        return new HvacDecision(HvacMode.APAGADO, coolingSetpointC, "Temperatura y humedad dentro de la banda de confort");
    }
}
