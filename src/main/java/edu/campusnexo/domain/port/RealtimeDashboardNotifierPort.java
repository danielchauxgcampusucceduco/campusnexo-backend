package edu.campusnexo.domain.port;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.SpaceTwinState;

/** Pushes digital-twin changes and architecture traces to connected dashboards. */
public interface RealtimeDashboardNotifierPort {
    void publishTwinUpdate(SpaceTwinState state);

    void publishTrace(ArchitectureTrace trace);
}
