package edu.campusnexo.application.port.in;

/** Live demonstration scenarios; {@code source} names the adapter that started one, for its trace. */
public interface SimulationUseCase {
    SimulationResult run(SimulationScenario scenario, String source);
}
