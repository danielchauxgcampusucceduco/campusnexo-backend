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
            new AlertThresholds(1000, 0.15, Duration.ofMinutes(15), Duration.ofMinutes(15));
    private final AlertRecorder recorder = new AlertRecorder(alerts);

    private static TelemetryRecorded event(Telemetry reading) {
        return new TelemetryRecorded(reading, TestData.andeanClassroom());
    }

    @Test
    @DisplayName("CO2 alto genera una alerta y no se repite dentro del periodo de enfriamiento")
    void co2AlertWithCooldown() {
        AirQualityAlertObserver observer = new AirQualityAlertObserver(recorder, thresholds);
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW, 20, 1600, 2)));
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW.plusSeconds(300), 20, 1700, 2)));
        assertEquals(1, alerts.rows().size());
        assertEquals(AlertSeverity.CRITICA, alerts.rows().get(0).severity());
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW.plusSeconds(1200), 20, 1100, 2)));
        assertEquals(2, alerts.rows().size());
    }

    @Test
    @DisplayName("Sobrecupo y consumo en espacio vacío se detectan por separado")
    void capacityAndEnergyWaste() {
        new CapacityAlertObserver(recorder, thresholds)
                .onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW, 40, 800, 2)));
        new EnergyWasteAlertObserver(recorder, thresholds)
                .onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW, 0, 450, 1.2)));
        assertEquals(AlertType.SOBRECUPO, alerts.rows().get(0).type());
        assertEquals(AlertSeverity.CRITICA, alerts.rows().get(0).severity());
        assertEquals(AlertType.CONSUMO_ESPACIO_VACIO, alerts.rows().get(1).type());
    }

    @Test
    @DisplayName("Reserva sin ocupantes después del margen genera una sola alerta de reserva sin uso")
    void reservationNoShow() {
        Instant start = TestData.NOW.minus(Duration.ofMinutes(20));
        reservations.save(new Reservation(UUID.randomUUID(), "AULA-301", start, start.plus(Duration.ofHours(2)), 25, "IS-701"));
        ReservationNoShowObserver observer = new ReservationNoShowObserver(recorder, reservations, thresholds);

        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", start.plus(Duration.ofMinutes(5)), 0, 450, 0.1)));
        assertTrue(alerts.rows().isEmpty());
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW, 0, 450, 0.1)));
        observer.onTelemetryRecorded(event(TestData.reading("AULA-301", TestData.NOW.plus(Duration.ofMinutes(30)), 0, 450, 0.1)));
        assertEquals(1, alerts.rows().size());
        assertEquals(AlertType.RESERVA_SIN_USO, alerts.rows().get(0).type());
    }
}
