package edu.campusnexo.domain.vision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.shared.DomainValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudyZoneRoiTest {
    private static final Instant T0 = Instant.parse("2026-10-20T15:00:00Z");
    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

    /** L-shaped (concave) polygon: the notch at the top right is outside. */
    private final StudyZoneRoi lShape = new StudyZoneRoi("Z1", "BIB-PISO-2", "Mesa L", List.of(
            new NormalizedPoint(0.1, 0.1), new NormalizedPoint(0.5, 0.1), new NormalizedPoint(0.5, 0.3),
            new NormalizedPoint(0.3, 0.3), new NormalizedPoint(0.3, 0.6), new NormalizedPoint(0.1, 0.6)));

    private static TrackedDetection detection(long id, DetectedObjectClass type, double x, double y) {
        return new TrackedDetection(id, type, 0.9, new BoundingBox(x - 0.02, y - 0.02, x + 0.02, y + 0.02));
    }

    @Test
    @DisplayName("Ray casting distingue puntos dentro y fuera de un polígono cóncavo")
    void rayCastingOnConcavePolygon() {
        assertTrue(lShape.containsPoint(new NormalizedPoint(0.2, 0.2)));
        assertTrue(lShape.containsPoint(new NormalizedPoint(0.2, 0.5)));
        assertTrue(lShape.containsPoint(new NormalizedPoint(0.45, 0.2)));
        assertFalse(lShape.containsPoint(new NormalizedPoint(0.45, 0.5)));
        assertFalse(lShape.containsPoint(new NormalizedPoint(0.05, 0.2)));
        assertFalse(lShape.containsPoint(new NormalizedPoint(0.2, 0.7)));
    }

    @Test
    @DisplayName("Un ROI necesita al menos tres vértices")
    void requiresAPolygon() {
        assertThrows(DomainValidationException.class, () -> new StudyZoneRoi("Z", "S", "Mesa",
                List.of(new NormalizedPoint(0, 0), new NormalizedPoint(1, 1))));
    }

    @Test
    @DisplayName("Una persona en el puesto lo marca ocupado y reinicia el cronómetro")
    void personVerifiesDesk() {
        DeskObservation held = new DeskObservation("Z1", ZoneDeskState.ALERTA_ACAPARAMIENTO, 0, 1, T0, T0);
        DeskObservation result = lShape.evaluateDeskGhosting(List.of(
                detection(1, DetectedObjectClass.PERSON, 0.2, 0.2), detection(2, DetectedObjectClass.LAPTOP, 0.2, 0.4)),
                held, T0.plusSeconds(120), FIFTEEN_MINUTES);
        assertEquals(ZoneDeskState.OCUPADO_VERIFICADO, result.state());
        assertNull(result.unattendedSince());
        assertEquals(1, result.peopleInside());
    }

    @Test
    @DisplayName("Pertenencias sin persona: alerta, y a los 15 minutos el puesto se libera")
    void hoardingTimeline() {
        List<TrackedDetection> backpackOnly = List.of(detection(7, DetectedObjectClass.BACKPACK, 0.2, 0.2));
        DeskObservation first = lShape.evaluateDeskGhosting(backpackOnly, null, T0, FIFTEEN_MINUTES);
        assertEquals(ZoneDeskState.ALERTA_ACAPARAMIENTO, first.state());
        assertEquals(T0, first.unattendedSince());

        DeskObservation tenMinutes = lShape.evaluateDeskGhosting(backpackOnly, first, T0.plus(Duration.ofMinutes(10)), FIFTEEN_MINUTES);
        assertEquals(ZoneDeskState.ALERTA_ACAPARAMIENTO, tenMinutes.state());
        assertEquals(10, tenMinutes.unattendedMinutes());

        DeskObservation released = lShape.evaluateDeskGhosting(backpackOnly, tenMinutes, T0.plus(FIFTEEN_MINUTES), FIFTEEN_MINUTES);
        assertEquals(ZoneDeskState.LIBERADO_POR_ABANDONO, released.state());
    }

    @Test
    @DisplayName("El puesto no hereda el abandono de otro ROI ni de un estado distinto")
    void abandonmentTimerBelongsToItsDeskAndState() {
        List<TrackedDetection> backpackOnly = List.of(detection(7, DetectedObjectClass.BACKPACK, 0.2, 0.2));
        DeskObservation otherDesk = new DeskObservation("Z2", ZoneDeskState.LIBERADO_POR_ABANDONO,
                0, 1, T0.minus(Duration.ofMinutes(30)), T0);
        DeskObservation wrongZone = lShape.evaluateDeskGhosting(backpackOnly, otherDesk, T0, FIFTEEN_MINUTES);
        DeskObservation occupied = new DeskObservation("Z1", ZoneDeskState.OCUPADO_VERIFICADO,
                0, 1, T0.minus(Duration.ofMinutes(30)), T0);
        DeskObservation wrongState = lShape.evaluateDeskGhosting(backpackOnly, occupied, T0, FIFTEEN_MINUTES);

        assertEquals(ZoneDeskState.ALERTA_ACAPARAMIENTO, wrongZone.state());
        assertEquals(0, wrongZone.unattendedMinutes());
        assertEquals(ZoneDeskState.ALERTA_ACAPARAMIENTO, wrongState.state());
        assertEquals(0, wrongState.unattendedMinutes());
    }

    @Test
    @DisplayName("Objetos fuera del polígono no cuentan; sin nada dentro el puesto está disponible")
    void emptyDesk() {
        DeskObservation result = lShape.evaluateDeskGhosting(
                List.of(detection(3, DetectedObjectClass.BOOK, 0.45, 0.5)), null, T0, FIFTEEN_MINUTES);
        assertEquals(ZoneDeskState.DISPONIBLE, result.state());
    }
}
