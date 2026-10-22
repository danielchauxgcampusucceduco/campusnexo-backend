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

/**
 * Effects of a stored decision: the HVAC order when the strategy changed, the Arduino order when the room
 * was idle with equipment on, the dashboard update and the architecture trace.
 */
public final class TwinPublisher {
    private final SpaceRepositoryPort twins;
    private final HvacActuatorCommandPort actuator;
    private final RoomEquipmentCommandPort equipmentActuator;
    private final TwinChangeNotifierPort notifier;
    private final RecentTraceBuffer traceBuffer;

    public TwinPublisher(SpaceRepositoryPort twins, HvacActuatorCommandPort actuator,
                         RoomEquipmentCommandPort equipmentActuator, TwinChangeNotifierPort notifier,
                         RecentTraceBuffer traceBuffer) {
        this.twins = twins;
        this.actuator = actuator;
        this.equipmentActuator = equipmentActuator;
        this.notifier = notifier;
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
        notifier.publishTwinUpdate(saved);
        trace.outbound("TwinChangeNotifierPort.publishTwinUpdate", notifier, "Gemelo actualizado");

        ArchitectureTrace finished = trace.finish();
        traceBuffer.add(finished);
        notifier.publishTrace(finished);
        return saved;
    }
}
