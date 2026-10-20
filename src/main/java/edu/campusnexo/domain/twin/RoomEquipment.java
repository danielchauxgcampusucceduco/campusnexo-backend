package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_DEVICES;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MAX_ILLUMINANCE_LUX;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.MIN_ILLUMINANCE_LUX;

/**
 * Electrical state of a room reported by its Arduino: whether the lighting circuit is on, the light level
 * from the light sensor, and how many of the room's computers draw power.
 */
public record RoomEquipment(boolean lightsOn, double illuminanceLux, int devicesOn, int devicesTotal) {
    public RoomEquipment {
        requireRange(illuminanceLux, MIN_ILLUMINANCE_LUX, MAX_ILLUMINANCE_LUX, "illuminanceLux");
        requireRange(devicesTotal, 0, MAX_DEVICES, "devicesTotal");
        requireRange(devicesOn, 0, MAX_DEVICES, "devicesOn");
        require(devicesOn <= devicesTotal, "devicesOn no puede superar devicesTotal");
    }

    public static RoomEquipment off(int devicesTotal) {
        return new RoomEquipment(false, 0, 0, devicesTotal);
    }

    public boolean anythingOn() {
        return lightsOn || devicesOn > 0;
    }

    /** State expected after the Arduino executes an idle shutdown. */
    public RoomEquipment shutDown() {
        return new RoomEquipment(false, illuminanceLux, 0, devicesTotal);
    }
}
