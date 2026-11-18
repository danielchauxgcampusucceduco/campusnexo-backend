package edu.campusnexo.application.twin;

import edu.campusnexo.domain.twin.SpaceTwinState;

/**
 * Result of one decision over a room: the previous and the new twin and the trace recorded while deciding. A frame
 * that was not newer than the camera's last one leaves the twin unchanged ({@link #changed()} is false).
 */
record TwinOutcome(SpaceTwinState previous, SpaceTwinState state, TraceRecorder trace, boolean changed) {

    TwinOutcome(SpaceTwinState previous, SpaceTwinState state, TraceRecorder trace) {
        this(previous, state, trace, true);
    }

    static TwinOutcome unchanged(SpaceTwinState current) {
        return new TwinOutcome(current, current, null, false);
    }
}
