package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.domain.twin.TelemetrySnapshot;

/** Entry point of the edge pipeline: one camera + sensor frame updates the digital twin. */
public interface ProcessSpaceTelemetryUseCase {
    /**
     * @param inboundAdapter name of the adapter that received the frame (shown in the architecture trace)
     */
    SpaceTwinState process(TelemetrySnapshot snapshot, String inboundAdapter);
}
