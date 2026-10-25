package edu.campusnexo.domain.twin;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;
import static edu.campusnexo.domain.telemetry.MeasurementLimits.*;

import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.TrackedDetection;
import java.time.Instant;
import java.util.List;

/**
 * One frame of edge telemetry: tracked detections from the camera plus the room sensors.
 * When {@code cameraOnline} is false the detections are ignored by the occupancy filter.
 * {@code outdoorTemperatureC} is optional: the branch average is used when it is {@code null}.
 * {@code lightsOn} and {@code devicesOn} are optional too: frames from a camera without an Arduino in the
 * room leave them {@code null} and the last equipment state reported for the room is kept.
 * {@code emulated} marks frames that do not come from a real camera (a test source).
 */
public record TelemetrySnapshot(
        String spaceId,
        Instant capturedAt,
        boolean cameraOnline,
        List<TrackedDetection> detections,
        double co2Ppm,
        double indoorTemperatureC,
        Double outdoorTemperatureC,
        double humidityPct,
        double illuminanceLux,
        int wifiDevices,
        double activePowerKw,
        Boolean lightsOn,
        Integer devicesOn,
        boolean emulated) {

    public TelemetrySnapshot {
        requireText(spaceId, "spaceId");
        requireNonNull(capturedAt, "capturedAt");
        detections = detections == null ? List.of() : List.copyOf(detections);
        requireRange(co2Ppm, MIN_CO2_PPM, MAX_CO2_PPM, "co2Ppm");
        requireRange(indoorTemperatureC, MIN_TEMPERATURE_C, MAX_TEMPERATURE_C, "indoorTemperatureC");
        if (outdoorTemperatureC != null) {
            requireRange(outdoorTemperatureC, MIN_TEMPERATURE_C, MAX_TEMPERATURE_C, "outdoorTemperatureC");
        }
        requireRange(humidityPct, MIN_HUMIDITY_PCT, MAX_HUMIDITY_PCT, "humidityPct");
        requireRange(illuminanceLux, MIN_ILLUMINANCE_LUX, MAX_ILLUMINANCE_LUX, "illuminanceLux");
        requireRange(wifiDevices, 0, MAX_DEVICES, "wifiDevices");
        requireRange(activePowerKw, MIN_POWER_KW, MAX_POWER_KW, "activePowerKw");
        if (devicesOn != null) {
            requireRange(devicesOn, 0, MAX_DEVICES, "devicesOn");
        }
    }

    /** Frame from a real camera. */
    public TelemetrySnapshot(String spaceId, Instant capturedAt, boolean cameraOnline, List<TrackedDetection> detections,
                             double co2Ppm, double indoorTemperatureC, Double outdoorTemperatureC, double humidityPct,
                             double illuminanceLux, int wifiDevices, double activePowerKw, Boolean lightsOn,
                             Integer devicesOn) {
        this(spaceId, capturedAt, cameraOnline, detections, co2Ppm, indoorTemperatureC, outdoorTemperatureC,
                humidityPct, illuminanceLux, wifiDevices, activePowerKw, lightsOn, devicesOn, false);
    }

    /** Frame without equipment data (camera only). */
    public TelemetrySnapshot(String spaceId, Instant capturedAt, boolean cameraOnline, List<TrackedDetection> detections,
                             double co2Ppm, double indoorTemperatureC, Double outdoorTemperatureC, double humidityPct,
                             double illuminanceLux, int wifiDevices, double activePowerKw) {
        this(spaceId, capturedAt, cameraOnline, detections, co2Ppm, indoorTemperatureC, outdoorTemperatureC,
                humidityPct, illuminanceLux, wifiDevices, activePowerKw, null, null, false);
    }

    /** People counted by the camera in this frame, or {@code null} when the camera is offline. */
    public Integer cameraPeopleCount() {
        if (!cameraOnline) {
            return null;
        }
        return (int) detections.stream().filter(d -> d.objectClass() == DetectedObjectClass.PERSON).count();
    }

    public int belongingsCount() {
        return (int) detections.stream().filter(d -> d.objectClass().isBelonging()).count();
    }
}
