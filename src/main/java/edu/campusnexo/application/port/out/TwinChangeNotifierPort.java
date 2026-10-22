package edu.campusnexo.application.port.out;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.SpaceTwinState;

/** Pushes every stored twin change and its architecture trace to whoever follows the campus live. */
public interface TwinChangeNotifierPort {
    void publishTwinUpdate(SpaceTwinState state);

    void publishTrace(ArchitectureTrace trace);
}
