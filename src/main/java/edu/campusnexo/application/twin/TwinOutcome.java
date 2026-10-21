package edu.campusnexo.application.twin;

import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.SpaceTwinState;

/**
 * Result of one decision over a room: the previous and the new twin, the order for the Arduino (if any)
 * and the trace recorded while deciding. Commands are sent only after the new twin is stored.
 */
record TwinOutcome(SpaceTwinState previous, SpaceTwinState state, EquipmentCommand equipmentCommand,
                   TraceRecorder trace) {
}
