package edu.campusnexo.domain.occupancy;

/** The estimator could not be reached or did not answer in time. */
public final class OccupancyEstimatorUnavailableException extends OccupancyEstimationFailedException {
    public OccupancyEstimatorUnavailableException(Throwable cause) {
        super("El servicio de IA no está disponible; inténtalo de nuevo más tarde.", cause);
    }
}
