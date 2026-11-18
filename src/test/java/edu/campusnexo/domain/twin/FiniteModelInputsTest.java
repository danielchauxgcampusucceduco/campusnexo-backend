package edu.campusnexo.domain.twin;

import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.campusnexo.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

class FiniteModelInputsTest {

    @Test
    void rejectsInfiniteKalmanCoefficients() {
        KalmanParameters kalman = KalmanParameters.defaults();
        assertThrows(DomainValidationException.class, () -> new KalmanParameters(
                kalman.processNoisePerMinute(), kalman.cameraVariance(), kalman.wifiRelativeError(),
                Double.POSITIVE_INFINITY));
        assertThrows(DomainValidationException.class, () -> new KalmanParameters(
                Double.NaN, kalman.cameraVariance(), kalman.wifiRelativeError(), kalman.peoplePerDevice()));
    }
}
