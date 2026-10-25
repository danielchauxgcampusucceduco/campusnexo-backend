package edu.campusnexo.application.port.in;

/** Sensor → AI → decision in one call: latest telemetry, occupancy estimate and recommendation. */
public interface SpaceEvaluationUseCase {
    SpaceEvaluation evaluate(String spaceId);
}
