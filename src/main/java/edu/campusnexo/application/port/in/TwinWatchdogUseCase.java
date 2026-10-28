package edu.campusnexo.application.port.in;

/**
 * Re-decides the rooms that stopped reporting. Without it, a camera that goes down sends nothing and nothing
 * notices; the same holds for what depends only on time (no-show, abandoned desks, idle equipment).
 */
public interface TwinWatchdogUseCase {
    /** Returns how many rooms were re-evaluated. */
    int vigilar();
}
