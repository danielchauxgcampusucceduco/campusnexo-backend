package edu.campusnexo.domain.trace;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

/** One hop of a request through the architecture; {@code offsetMicros} is measured from the trace start. */
public record TraceStep(HexagonalLayer layer, String component, String detail, long offsetMicros) {
    public TraceStep {
        requireNonNull(layer, "layer");
        requireText(component, "component");
        detail = detail == null ? "" : detail;
    }
}
