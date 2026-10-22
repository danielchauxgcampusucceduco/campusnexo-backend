package edu.campusnexo.support;

import edu.campusnexo.application.port.out.OperationalMetrics;
import java.util.ArrayList;
import java.util.List;

/** Records the operational events reported by the use cases. */
public final class RecordingMetrics implements OperationalMetrics {
    public final List<String> discarded = new ArrayList<>();
    public final List<String> fallbacks = new ArrayList<>();
    public final List<String> circuitStates = new ArrayList<>();
    public int conflicts;

    @Override
    public synchronized void readingDiscarded(String source, String reason) {
        discarded.add(source + ":" + reason);
    }

    @Override
    public synchronized void twinWriteConflict() {
        conflicts++;
    }

    @Override
    public synchronized void aiFallback(String reason) {
        fallbacks.add(reason);
    }

    @Override
    public synchronized void aiCircuitState(String state) {
        circuitStates.add(state);
    }
}
