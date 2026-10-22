package edu.campusnexo.support;

import edu.campusnexo.application.port.out.TwinChangeNotifierPort;
import edu.campusnexo.domain.port.HvacActuatorCommandPort;
import edu.campusnexo.domain.port.RoomEquipmentCommandPort;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.EquipmentCommand;
import edu.campusnexo.domain.twin.HvacCommand;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.ArrayList;
import java.util.List;

public final class RecordingOutboundAdapters {
    private RecordingOutboundAdapters() {
    }

    public static final class Actuator implements HvacActuatorCommandPort {
        public final List<HvacCommand> commands = new ArrayList<>();

        @Override
        public void send(HvacCommand command) {
            commands.add(command);
        }
    }

    public static final class EquipmentActuator implements RoomEquipmentCommandPort {
        public final List<EquipmentCommand> commands = new ArrayList<>();

        @Override
        public void send(EquipmentCommand command) {
            commands.add(command);
        }
    }

    public static final class Notifier implements TwinChangeNotifierPort {
        public final List<SpaceTwinState> updates = new ArrayList<>();
        public final List<ArchitectureTrace> traces = new ArrayList<>();

        @Override
        public void publishTwinUpdate(SpaceTwinState state) {
            updates.add(state);
        }

        @Override
        public void publishTrace(ArchitectureTrace trace) {
            traces.add(trace);
        }
    }
}
