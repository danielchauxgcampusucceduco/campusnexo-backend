package edu.campusnexo.application.twin;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import edu.campusnexo.domain.trace.HexagonalLayer;
import edu.campusnexo.domain.trace.TraceStep;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Collects the steps of one request; not thread-safe, one instance per request. */
public final class TraceRecorder {
    private final UUID traceId = UUID.randomUUID();
    private final String trigger;
    private final String spaceId;
    private final Instant startedAt;
    private final long startNanos = System.nanoTime();
    private final List<TraceStep> steps = new ArrayList<>();

    public TraceRecorder(String trigger, String spaceId, Instant startedAt) {
        this.trigger = trigger;
        this.spaceId = spaceId;
        this.startedAt = startedAt;
    }

    /** A step of a class of the core: its name comes from the class itself, so it cannot go stale. */
    public TraceRecorder step(HexagonalLayer layer, Class<?> component, String detail) {
        return step(layer, component.getSimpleName(), detail);
    }

    public TraceRecorder step(HexagonalLayer layer, String component, String detail) {
        steps.add(new TraceStep(layer, component, detail, (System.nanoTime() - startNanos) / 1_000));
        return this;
    }

    /** Records an output port call followed by the adapter that implements it. */
    public TraceRecorder outbound(String port, Object adapter, String detail) {
        step(HexagonalLayer.OUTPUT_PORT, port, detail);
        return step(HexagonalLayer.OUTBOUND_ADAPTER, adapterName(adapter), detail);
    }

    public ArchitectureTrace finish() {
        return new ArchitectureTrace(traceId, trigger, spaceId, startedAt, steps);
    }

    /** Simple name without proxy suffixes; anonymous classes and lambdas have none, so they get their full name. */
    private static String adapterName(Object adapter) {
        String name = adapter.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = adapter.getClass().getName().substring(adapter.getClass().getName().lastIndexOf('.') + 1);
        }
        int proxyMarker = name.indexOf('$');
        return proxyMarker > 0 ? name.substring(0, proxyMarker) : name;
    }
}
