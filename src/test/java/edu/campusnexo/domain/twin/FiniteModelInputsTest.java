package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.climate.ComfortBand;
import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class FiniteModelInputsTest {

    @Test
    void rejectsInfiniteThermalAndKalmanCoefficients() {
        ThermalParameters thermal = ThermalParameters.defaults();
        assertThrows(DomainValidationException.class, () -> new ThermalParameters(
                Double.POSITIVE_INFINITY, thermal.occupantGainCPerHour(), thermal.hvacRateCPerMinute(),
                thermal.stepMinutes(), thermal.defaultOccupiedMinutes(), thermal.preConditioningLeadMinutes(),
                thermal.ecoSetbackWindowMinutes(), thermal.ecoSetbackOffsetC(), thermal.co2VentilationPpm()));

        KalmanParameters kalman = KalmanParameters.defaults();
        assertThrows(DomainValidationException.class, () -> new KalmanParameters(
                kalman.processNoisePerMinute(), kalman.cameraVariance(), kalman.co2RelativeError(),
                kalman.wifiRelativeError(), Double.POSITIVE_INFINITY, kalman.outdoorCo2Ppm(),
                kalman.co2PpmPerPersonSqm()));
    }

    @Test
    void rejectsInfiniteThermalContextMeasurements() {
        ComfortBand band = new ComfortBand(19, 24);
        assertThrows(DomainValidationException.class,
                () -> new ThermalContext(22, 14, 10, Double.POSITIVE_INFINITY, 500, band, null, null));
        assertThrows(DomainValidationException.class,
                () -> new ThermalContext(22, 14, 10, 48, Double.POSITIVE_INFINITY, band, null, null));
    }
}
