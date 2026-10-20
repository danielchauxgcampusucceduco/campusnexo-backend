package edu.campusnexo.domain.occupancy;

/** The estimator answered, but with data that breaks the contract. */
public final class OccupancyEstimatorInvalidResponseException extends OccupancyEstimationFailedException {
    public OccupancyEstimatorInvalidResponseException(String reason, Throwable cause) {
        super("El servicio de IA devolvió una respuesta no válida: " + reason, cause);
    }
}
