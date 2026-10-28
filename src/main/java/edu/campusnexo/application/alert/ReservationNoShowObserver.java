package edu.campusnexo.application.alert;

import edu.campusnexo.application.port.out.TelemetryObserver;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.telemetry.Telemetry;
import edu.campusnexo.domain.telemetry.TelemetryRecorded;
import edu.campusnexo.domain.twin.SpaceStatus;
import java.util.Optional;

/** Detects rooms that are booked but empty after the grace period (one alert per reservation). */
public class ReservationNoShowObserver implements TelemetryObserver {
    private final AlertRecorder recorder;
    private final ReservationRepository reservations;
    private final SpaceRepositoryPort twins;
    private final AlertThresholds thresholds;

    public ReservationNoShowObserver(AlertRecorder recorder, ReservationRepository reservations,
                                     SpaceRepositoryPort twins, AlertThresholds thresholds) {
        this.recorder = recorder;
        this.reservations = reservations;
        this.twins = twins;
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
        // The twin remembers whether anyone came: an empty room after a class is not a no-show.
        boolean noShow = twins.findTwin(reading.spaceId())
                .map(twin -> twin.status() == SpaceStatus.LIBERADO_POR_NO_SHOW).orElse(!reservation.checkedIn());
        if (!noShow) {
            return;
        }
        Alert alert = Alert.raise(reading.spaceId(), AlertType.RESERVA_SIN_USO, AlertSeverity.ADVERTENCIA,
                reading.recordedAt(), "Reserva " + reservation.reference() + ": nadie llegó en los "
                        + thresholds.reservationGrace().toMinutes() + " min de gracia y no hubo check-in; el salón quedó liberado",
                0, reservation.expectedAttendees());
        recorder.recordUnlessRaisedSince(alert, reservation.startsAt());
    }
}
