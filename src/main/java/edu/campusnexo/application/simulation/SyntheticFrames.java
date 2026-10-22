package edu.campusnexo.application.simulation;

import edu.campusnexo.domain.twin.TelemetrySnapshot;
import edu.campusnexo.domain.vision.BoundingBox;
import edu.campusnexo.domain.vision.DetectedObjectClass;
import edu.campusnexo.domain.vision.NormalizedPoint;
import edu.campusnexo.domain.vision.StudyZoneRoi;
import edu.campusnexo.domain.vision.TrackedDetection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the camera frames of the demonstration with the geometry a ceiling camera would report: people in
 * the centre of the seat regions, extra people in the front band (outside every seat), belongings on desks.
 */
final class SyntheticFrames {
    private static final double OUTDOOR_C = 13.5;
    private static final double OUTDOOR_CO2_PPM = 420;
    private static final double PERSON_WIDTH = 0.045;
    private static final double PERSON_HEIGHT = 0.07;
    private static final double PERSON_CONFIDENCE = 0.91;
    private static final double OBJECT_CONFIDENCE = 0.78;

    private final String spaceId;

    SyntheticFrames(String spaceId) {
        this.spaceId = spaceId;
    }

    /**
     * The first {@code people} go to seats spread over the room (one person per seat region); anyone beyond
     * the seats stands in the front band.
     */
    TelemetrySnapshot seated(List<StudyZoneRoi> seats, Instant at, int people, double co2, double indoorC,
                             double humidity, double lux, int wifi, double powerKw) {
        List<TrackedDetection> detections = new ArrayList<>(Math.max(0, people));
        int seated = Math.min(people, seats.size());
        for (int i = 0; i < seated; i++) {
            // Every k-th seat, so a half-full room looks half full everywhere.
            int index = (int) Math.floor(i * (double) seats.size() / seated);
            detections.add(at(i + 1, DetectedObjectClass.PERSON, centroid(seats.get(index)), PERSON_WIDTH, PERSON_HEIGHT));
        }
        detections.addAll(aislePeople(people - seated, seated + 1));
        return new TelemetrySnapshot(spaceId, at, true, detections, co2, indoorC, OUTDOOR_C, humidity, lux, wifi, powerKw);
    }

    /** The same frame with the room board's relays: lights and computers drawing power. */
    TelemetrySnapshot withEquipment(TelemetrySnapshot frame, boolean lightsOn, int devicesOn) {
        return new TelemetrySnapshot(frame.spaceId(), frame.capturedAt(), frame.cameraOnline(), frame.detections(),
                frame.co2Ppm(), frame.indoorTemperatureC(), frame.outdoorTemperatureC(), frame.humidityPct(),
                frame.illuminanceLux(), frame.wifiDevices(), frame.activePowerKw(), lightsOn, devicesOn);
    }

    TelemetrySnapshot cameraOffline(Instant at, double co2, double indoorC, double humidity, double lux, int wifi,
                                    double powerKw) {
        return new TelemetrySnapshot(spaceId, at, false, List.of(), co2, indoorC, OUTDOOR_C, humidity, lux, wifi, powerKw);
    }

    /**
     * A library frame: belongings without a student on {@code heldDesks}, a student with a laptop on
     * {@code seatedDesks}, and {@code walkers} people in the aisle.
     */
    TelemetrySnapshot library(List<StudyZoneRoi> desks, Instant at, List<Integer> heldDesks, List<Integer> seatedDesks,
                              int walkers, double areaSqm) {
        List<TrackedDetection> detections = new ArrayList<>();
        long track = 100;
        for (int desk : heldDesks) {
            NormalizedPoint centre = centroid(desks.get(desk));
            detections.add(at(track++, desk == 0 ? DetectedObjectClass.LAPTOP : DetectedObjectClass.BOOK, centre, 0.05, 0.04));
            if (desk == 0) {
                detections.add(at(track++, DetectedObjectClass.BACKPACK, offset(centre, 0.04, 0.02), 0.05, 0.06));
            }
        }
        for (int desk : seatedDesks) {
            NormalizedPoint centre = centroid(desks.get(desk));
            detections.add(at(track++, DetectedObjectClass.PERSON, centre, PERSON_WIDTH, PERSON_HEIGHT));
            detections.add(at(track++, DetectedObjectClass.LAPTOP, offset(centre, 0.03, 0.03), 0.05, 0.04));
        }
        detections.addAll(aislePeople(walkers, track));
        int people = seatedDesks.size() + walkers;
        // CO2 and Wi-Fi consistent with the people seen: the steady-state rise of 1000 ppm·m² per person.
        double co2 = OUTDOOR_CO2_PPM + people * 1000 / areaSqm;
        return new TelemetrySnapshot(spaceId, at, true, detections, co2, 19.6, OUTDOOR_C, 52, 450,
                (int) Math.round(people / 0.8), 2.1);
    }

    /** People standing in the front band y ∈ [0.07, 0.25], which never overlaps a seat region. */
    private static List<TrackedDetection> aislePeople(int count, long firstTrackId) {
        List<TrackedDetection> people = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            double x = 0.04 + (i % 12) * 0.078;
            double y = 0.08 + ((i / 12) % 3) * 0.07;
            people.add(at(firstTrackId + i, DetectedObjectClass.PERSON, new NormalizedPoint(x, y), PERSON_WIDTH, PERSON_HEIGHT));
        }
        return people;
    }

    private static TrackedDetection at(long trackId, DetectedObjectClass type, NormalizedPoint center, double width,
                                       double height) {
        double confidence = type == DetectedObjectClass.PERSON ? PERSON_CONFIDENCE : OBJECT_CONFIDENCE;
        return new TrackedDetection(trackId, type, confidence, new BoundingBox(clamp(center.x() - width / 2),
                clamp(center.y() - height / 2), clamp(center.x() + width / 2), clamp(center.y() + height / 2)));
    }

    private static NormalizedPoint centroid(StudyZoneRoi roi) {
        double x = roi.polygon().stream().mapToDouble(NormalizedPoint::x).average().orElseThrow();
        double y = roi.polygon().stream().mapToDouble(NormalizedPoint::y).average().orElseThrow();
        return new NormalizedPoint(x, y);
    }

    private static NormalizedPoint offset(NormalizedPoint point, double dx, double dy) {
        return new NormalizedPoint(clamp(point.x() + dx), clamp(point.y() + dy));
    }

    private static double clamp(double value) {
        return Math.min(1, Math.max(0, value));
    }
}
