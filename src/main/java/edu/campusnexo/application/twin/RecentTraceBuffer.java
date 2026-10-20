package edu.campusnexo.application.twin;

import edu.campusnexo.domain.trace.ArchitectureTrace;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Bounded, thread-safe history of the latest traces, newest first. */
public final class RecentTraceBuffer {
    private final int capacity;
    private final Deque<ArchitectureTrace> traces = new ArrayDeque<>();

    public RecentTraceBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity debe ser al menos 1");
        }
        this.capacity = capacity;
    }

    public synchronized void add(ArchitectureTrace trace) {
        traces.addFirst(trace);
        while (traces.size() > capacity) {
            traces.removeLast();
        }
    }

    public synchronized List<ArchitectureTrace> latest(int limit) {
        List<ArchitectureTrace> result = new ArrayList<>(Math.min(limit, traces.size()));
        for (ArchitectureTrace trace : traces) {
            if (result.size() == limit) {
                break;
            }
            result.add(trace);
        }
        return result;
    }
}
