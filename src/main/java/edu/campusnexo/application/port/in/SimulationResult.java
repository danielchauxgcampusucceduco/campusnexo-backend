package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import java.util.List;

/** Twins affected by the scenario and the trace of the last event it produced ({@code null} for resets). */
public record SimulationResult(SimulationScenario scenario, List<SpaceTwinView> spaces, ArchitectureTrace trace) {
}
