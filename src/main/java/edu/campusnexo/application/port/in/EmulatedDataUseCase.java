package edu.campusnexo.application.port.in;

import java.util.List;

/** Leaving the demo: no emulated figure stays on the campus. */
public interface EmulatedDataUseCase {
    /** Erases everything about the rooms fed by an emulated sensor and starts their twins again; their ids. */
    List<String> purge();
}
