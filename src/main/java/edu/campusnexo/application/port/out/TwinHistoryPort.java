package edu.campusnexo.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * Append-only history of the twin (one entry per stored change), kept apart from the transactional state so it
 * can grow without slowing the pipeline. It feeds the occupancy forecast and the datasets that evaluate the AI.
 */
public interface TwinHistoryPort {
    /** Whether a history store is configured; without one every query is empty. */
    boolean available();

    /** Occupancy of the space as the twin stored it between {@code from} and {@code to}, oldest first. */
    List<OccupancySample> occupancy(String spaceId, Instant from, Instant to);

    /**
     * One stored change of a room: the fused count, the people by role, the camera's own count ({@code null}
     * when the camera did not see the room) and the CO₂. {@code emulated} marks entries not fed by a real camera.
     */
    record OccupancySample(Instant at, String spaceId, int people, double estimate, double stdDev, int teachers,
                           int students, Integer cameraPeople, double co2Ppm, boolean cameraOnline, boolean emulated,
                           List<String> escenarios) {
        public OccupancySample {
            escenarios = List.copyOf(escenarios);
        }
    }
}
