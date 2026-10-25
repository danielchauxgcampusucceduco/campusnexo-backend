package edu.campusnexo.domain.climate;

import static edu.campusnexo.domain.shared.DomainChecks.require;

/**
 * Compressor protection: once the unit starts it must run a minimum time, and once it stops it must rest a
 * minimum time. Short cycling wears the compressor and wastes the energy of every start.
 */
public record CompressorGuard(int minimumOnMinutes, int minimumOffMinutes) {
    public CompressorGuard {
        require(minimumOnMinutes >= 0 && minimumOffMinutes >= 0, "Los tiempos mínimos del compresor no pueden ser negativos");
    }

    /** Ten minutes on and five minutes off, the usual manufacturer minimums for small split units. */
    public static CompressorGuard standard() {
        return new CompressorGuard(10, 5);
    }

    /**
     * Whether the requested change must wait: switching off before the minimum on time, or switching on
     * before the minimum rest time.
     */
    public boolean mustHold(boolean onNow, boolean onRequested, long minutesInCurrentState) {
        if (onNow && !onRequested) {
            return minutesInCurrentState < minimumOnMinutes;
        }
        if (!onNow && onRequested) {
            return minutesInCurrentState < minimumOffMinutes;
        }
        return false;
    }

    public int minimumFor(boolean onNow) {
        return onNow ? minimumOnMinutes : minimumOffMinutes;
    }
}
