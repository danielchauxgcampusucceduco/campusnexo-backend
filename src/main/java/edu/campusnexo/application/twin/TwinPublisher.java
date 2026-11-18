package edu.campusnexo.application.twin;

import edu.campusnexo.application.port.out.TwinChangeNotifierPort;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.util.List;

/**
 * Effects of a stored decision: the twin change and the architecture trace for every follower (the live dashboard,
 * the history store). The change is already committed, so a follower that fails is skipped and the others still
 * get it; each adapter logs its own failures.
 */
public final class TwinPublisher {
    private final SpaceRepositoryPort twins;
    private final List<TwinChangeNotifierPort> notifiers;
    private final RecentTraceBuffer traceBuffer;

    public TwinPublisher(SpaceRepositoryPort twins, List<TwinChangeNotifierPort> notifiers,
                         RecentTraceBuffer traceBuffer) {
        this.twins = twins;
        this.notifiers = List.copyOf(notifiers);
        this.traceBuffer = traceBuffer;
    }

    SpaceTwinState publish(TwinOutcome outcome) {
        SpaceTwinState saved = outcome.state();
        TraceRecorder trace = outcome.trace();
        trace.outbound("SpaceRepositoryPort.saveTwin", twins, saved.status() + ", " + saved.occupancy().people()
                + " personas");
        // Observer: one output port, as many subscribers as adapters (the dashboard by STOMP, the history in MongoDB).
        trace.step(HexagonalLayer.OUTPUT_PORT, "TwinChangeNotifierPort.publishTwinUpdate",
                "Observer: avisa a " + notifiers.size() + (notifiers.size() == 1 ? " suscriptor" : " suscriptores"));
        for (TwinChangeNotifierPort notifier : notifiers) {
            try {
                notifier.publishTwinUpdate(saved);
                trace.adapter(notifier, "Gemelo actualizado");
            } catch (RuntimeException failure) {
                trace.adapter(notifier, "Omitido: " + failure.getMessage());
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
