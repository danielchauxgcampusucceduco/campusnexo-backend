package edu.campusnexo.domain.climate.policy;

import edu.campusnexo.domain.climate.ClimateContext;
import edu.campusnexo.domain.climate.HvacDecision;
import edu.campusnexo.domain.climate.HvacMode;

/** Cold highland climate: the usual problem is low temperature, so heating is evaluated first. */
public final class HeatingFirstThermalPolicy implements ThermalPolicy {
    private final double heatingSetpointC;
    private final double coolingSetpointC;
    private final double deadbandC;

    public HeatingFirstThermalPolicy(double heatingSetpointC, double coolingSetpointC, double deadbandC) {
        this.heatingSetpointC = heatingSetpointC;
        this.coolingSetpointC = coolingSetpointC;
        this.deadbandC = deadbandC;
    }

    @Override
    public HvacDecision recommend(ClimateContext context) {
        double indoor = context.indoorTemperatureC();
        if (!context.occupied()) {
            return new HvacDecision(HvacMode.APAGADO, heatingSetpointC, "Sin ocupación; no se climatiza el espacio");
        }
        if (indoor < heatingSetpointC - deadbandC) {
            return new HvacDecision(HvacMode.CALEFACCION, heatingSetpointC,
                    "Temperatura interior bajo el setpoint de calefacción con ocupación presente");
        }
        if (indoor > coolingSetpointC + deadbandC) {
            return new HvacDecision(HvacMode.ENFRIAMIENTO, coolingSetpointC,
                    "Temperatura interior sobre el límite de confort con ocupación presente");
        }
        return new HvacDecision(HvacMode.APAGADO, heatingSetpointC, "Temperatura interior dentro de la banda de confort");
    }
}
