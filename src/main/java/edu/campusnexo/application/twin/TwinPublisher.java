package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.out.TwinChangeNotifierPort;
import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.port.RoomEquipmentCommandPort;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.HvacCommand;
import edu.campusnexo.domain.twin.HvacControlState;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.List;

/**
 * Effects of a stored decision: the HVAC order when the strategy changed, the Arduino order when the room
 * was idle with equipment on, and the twin change and architecture trace for every follower (the live dashboard,
 * the history store). The change is already committed, so a follower that fails is skipped and the others still
 * get it; each adapter logs its own failures.
 */
public final class TwinPublisher {
    private final SpaceRepositoryPort twins;
    private final HvacActuatorCommandPort actuator;
    private final RoomEquipmentCommandPort equipmentActuator;
    private final List<TwinChangeNotifierPort> notifiers;
    private final RecentTraceBuffer traceBuffer;

    public TwinPublisher(SpaceRepositoryPort twins, HvacActuatorCommandPort actuator,
                         RoomEquipmentCommandPort equipmentActuator, List<TwinChangeNotifierPort> notifiers,
                         RecentTraceBuffer traceBuffer) {
        this.twins = twins;
        this.actuator = actuator;
        this.equipmentActuator = equipmentActuator;
        this.notifiers = List.copyOf(notifiers);
        this.traceBuffer = traceBuffer;
    }

    SpaceTwinState publish(TwinOutcome outcome) {
        SpaceTwinState previous = outcome.previous();
        SpaceTwinState saved = outcome.state();
        TraceRecorder trace = outcome.trace();
        trace.outbound("SpaceRepositoryPort.saveTwin", twins, saved.status() + " / " + saved.hvacState());

        boolean hasUnit = saved.hvacState() != HvacControlState.SIN_CLIMATIZACION;
        boolean settingChanged = saved.hvac().differsFrom(previous.hvac()) || previous.lastKalmanStep() == null;
        if (hasUnit && settingChanged) {
            actuator.send(HvacCommand.of(saved.spaceId(), saved.hvac(), saved.hvac().reason(), saved.updatedAt()));
            trace.outbound("HvacActuatorCommandPort.send", actuator, previous.hvacState() + " → " + saved.hvacState()
                    + " (" + saved.hvacMode() + ")");
        }
        EquipmentCommand command = outcome.equipmentCommand();
        if (command != null) {
            equipmentActuator.send(command);
            trace.outbound("RoomEquipmentCommandPort.send", equipmentActuator,
                    (command.lightsOff() ? "apagar luces" : "") + (command.lightsOff() && command.suspendDevices() ? " y " : "")
                            + (command.suspendDevices() ? "suspender equipos" : ""));
        }
        for (TwinChangeNotifierPort notifier : notifiers) {
            try {
                notifier.publishTwinUpdate(saved);
                trace.outbound("TwinChangeNotifierPort.publishTwinUpdate", notifier, "Gemelo actualizado");
            } catch (RuntimeException failure) {
                trace.outbound("TwinChangeNotifierPort.publishTwinUpdate", notifier, "Omitido: " + failure.getMessage());
            }
        }

        ArchitectureTrace finished = trace.finish();
        traceBuffer.add(finished);
        for (TwinChangeNotifierPort notifier : notifiers) {
            try {
                notifier.publishTrace(finished);
            } catch (RuntimeException failure) {
                // Already logged by the adapter; the trace is in the buffer and the other followers got it.
            }
        }
        return saved;
    }
}
