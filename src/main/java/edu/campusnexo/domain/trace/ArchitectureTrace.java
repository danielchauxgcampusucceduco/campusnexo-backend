package edu.campusnexo.domain.trace;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ordered path of one event through adapters, ports, use case and domain services. */
public record ArchitectureTrace(UUID traceId, String trigger, String spaceId, Instant startedAt, List<TraceStep> steps) {
    public ArchitectureTrace {
        requireNonNull(traceId, "traceId");
        requireText(trigger, "trigger");
        requireNonNull(startedAt, "startedAt");
        steps = List.copyOf(steps);
    }
}
