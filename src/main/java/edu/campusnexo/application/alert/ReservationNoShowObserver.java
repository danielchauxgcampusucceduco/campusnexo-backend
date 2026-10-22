package edu.campusnexo.application.alert;

import edu.campusnexo.application.port.out.TelemetryObserver;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import java.util.Optional;

/** Detects rooms that are booked but empty after the grace period (one alert per reservation). */
public class ReservationNoShowObserver implements TelemetryObserver {
    private final AlertRecorder recorder;
    private final ReservationRepository reservations;
    private final AlertThresholds thresholds;

    public ReservationNoShowObserver(AlertRecorder recorder, ReservationRepository reservations,
                                     AlertThresholds thresholds) {
        this.recorder = recorder;
        this.reservations = reservations;
        this.thresholds = thresholds;
    }

    @Override
    public void onTelemetryRecorded(TelemetryRecorded event) {
        Telemetry reading = event.telemetry();
        if (!reading.isEmpty()) {
            return;
        }
        Optional<Reservation> active = reservations.findActiveAt(reading.spaceId(), reading.recordedAt());
        if (active.isEmpty()) {
            return;
        }
        Reservation reservation = active.get();
        if (reading.recordedAt().isBefore(reservation.startsAt().plus(thresholds.reservationGrace()))) {
            return;
        }
        Alert alert = Alert.raise(reading.spaceId(), AlertType.RESERVA_SIN_USO, AlertSeverity.ADVERTENCIA,
                reading.recordedAt(), "Reserva " + reservation.reference() + " sin ocupantes "
                        + thresholds.reservationGrace().toMinutes() + " min después de iniciar; considerar liberar el espacio",
                0, reservation.expectedAttendees());
        recorder.recordUnlessRaisedSince(alert, reservation.startsAt());
    }
}
