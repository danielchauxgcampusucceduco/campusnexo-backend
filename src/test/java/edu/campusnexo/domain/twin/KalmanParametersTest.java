package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class KalmanParametersTest {

    @Test
    void exposesReferenceNoiseAndSensorCoefficients() {
        KalmanParameters parameters = KalmanParameters.defaults();

        assertEquals(0.8, parameters.processNoisePerMinute());
        assertEquals(0.64, parameters.cameraVariance());
        assertEquals(0.8, parameters.peoplePerDevice());
    }

    @Test
    void rejectsZeroMeasurementVariance() {
        KalmanParameters defaults = KalmanParameters.defaults();
        assertThrows(DomainValidationException.class, () -> new KalmanParameters(
                defaults.processNoisePerMinute(), 0, defaults.wifiRelativeError(), defaults.peoplePerDevice()));
    }
}
