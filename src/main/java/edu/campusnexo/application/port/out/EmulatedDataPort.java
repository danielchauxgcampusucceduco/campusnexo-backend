package edu.campusnexo.application.port.out;

/**
 * Erases, in the transactional store, what emulated sensors left about a room: its telemetry, occupancy estimates,
 * climate recommendations, alerts and the desk proposals of an emulated camera.
 */
public interface EmulatedDataPort {
    void forget(String spaceId);
}
