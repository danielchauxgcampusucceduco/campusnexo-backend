package edu.campusnexo.application.twin;

import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.SpaceTwinState;

/**
 * Result of one decision over a room: the previous and the new twin, the order for the Arduino (if any)
 * and the trace recorded while deciding. Commands are sent only after the new twin is stored. A reading that
 * was not newer than the last one of its device leaves the twin unchanged ({@link #changed()} is false).
 */
record TwinOutcome(SpaceTwinState previous, SpaceTwinState state, EquipmentCommand equipmentCommand,
                   TraceRecorder trace, boolean changed) {

    TwinOutcome(SpaceTwinState previous, SpaceTwinState state, EquipmentCommand equipmentCommand, TraceRecorder trace) {
        this(previous, state, equipmentCommand, trace, true);
    }

    static TwinOutcome unchanged(SpaceTwinState current) {
        return new TwinOutcome(current, current, null, null, false);
    }
}
