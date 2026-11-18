package edu.campusnexo.support;

import edu.campusnexo.application.port.out.TwinChangeNotifierPort;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.ArrayList;
import java.util.List;

public final class RecordingOutboundAdapters {
    private RecordingOutboundAdapters() {
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
