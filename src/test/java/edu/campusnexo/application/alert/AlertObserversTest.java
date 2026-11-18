package edu.campusnexo.application.alert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import edu.campusnexo.support.InMemoryAlertRepository;
import edu.campusnexo.support.InMemoryReservationRepository;
import edu.campusnexo.support.InMemorySpaceRepositoryPort;
import edu.campusnexo.domain.twin.SpaceStatus;
import edu.campusnexo.domain.twin.SpaceTwinState;
import edu.campusnexo.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AlertObserversTest {
    private final InMemoryAlertRepository alerts = new InMemoryAlertRepository();
    private final InMemoryReservationRepository reservations = new InMemoryReservationRepository();
    private final AlertThresholds thresholds =
            new AlertThresholds(Duration.ofMinutes(15), Duration.ofMinutes(15));
    private final AlertRecorder recorder = new AlertRecorder(alerts);

    private static TelemetryRecorded event(Telemetry reading) {
        return new TelemetryRecorded(reading, TestData.andeanClassroom());
    }

    @Test
    @DisplayName("El sobrecupo genera una alerta crítica y no se repite dentro del periodo de enfriamiento")
    void capacityAlertWithCooldown() {
        CapacityAlertObserver observer = new CapacityAlertObserver(recorder, thresholds);
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW, 40)));
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW.plusSeconds(300), 40)));
        assertEquals(1, alerts.rows().size());
        assertEquals(AlertType.SOBRECUPO, alerts.rows().get(0).type());
        assertEquals(AlertSeverity.CRITICA, alerts.rows().get(0).severity());
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW.plusSeconds(1200), 40)));
        assertEquals(2, alerts.rows().size());
    }

    @Test
    @DisplayName("Reserva sin ocupantes después del margen genera una sola alerta de reserva sin uso")
    void reservationNoShow() {
        Instant start = TestData.NOW.minus(Duration.ofMinutes(20));
        reservations.save(new Reservation(UUID.randomUUID(), "AULA-301", start, start.plus(Duration.ofHours(2)), 25, "IS-701"));
        InMemorySpaceRepositoryPort twins = new InMemorySpaceRepositoryPort();
        twins.overwrite(SpaceTwinState.initial(TestData.andeanClassroom(), start).toBuilder()
                .status(SpaceStatus.LIBERADO_POR_NO_SHOW).build());
        ReservationNoShowObserver observer = new ReservationNoShowObserver(recorder, reservations, twins, thresholds);

        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", start.plus(Duration.ofMinutes(5)), 0)));
        assertTrue(alerts.rows().isEmpty());
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW, 0)));
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW.plus(Duration.ofMinutes(30)), 0)));
        assertEquals(1, alerts.rows().size());
        assertEquals(AlertType.RESERVA_SIN_USO, alerts.rows().get(0).type());
    }

    @Test
    @DisplayName("Si hubo clase y el salón quedó vacío, no es reserva sin uso")
    void attendedReservationIsNotANoShow() {
        Instant start = TestData.NOW.minus(Duration.ofMinutes(40));
        reservations.save(new Reservation(UUID.randomUUID(), "AULA-301", start, start.plus(Duration.ofHours(2)), 25, "IS-701"));
        InMemorySpaceRepositoryPort twins = new InMemorySpaceRepositoryPort();
        twins.overwrite(SpaceTwinState.initial(TestData.andeanClassroom(), start).toBuilder()
                .status(SpaceStatus.LIBRE).build());
        new ReservationNoShowObserver(recorder, reservations, twins, thresholds)
                .onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW, 0)));

        assertTrue(alerts.rows().isEmpty());
    }
}
