package edu.campusnexo.domain.twin;

import edu.campusnexo.domain.reservation.Reservation;
import java.time.Duration;
import java.time.Instant;

/**
 * Classifies a space from its estimated occupancy, capacity, active reservation and manual controls.
 * Precedence: maintenance, pop-up study room, over-capacity, reservation rules, free use.
 */
public final class SpaceStatusPolicy {
    private final Duration noShowGrace;
    private final double underuseRatio;

    /**
     * @param noShowGrace   time after a reservation starts before an empty room counts as a no-show
     * @param underuseRatio occupancy below this share of the expected attendees is underuse
     */
    public SpaceStatusPolicy(Duration noShowGrace, double underuseRatio) {
        this.noShowGrace = noShowGrace;
        this.underuseRatio = underuseRatio;
    }

    public Duration noShowGrace() {
        return noShowGrace;
    }

    public SpaceStatus classify(boolean maintenance, boolean popUpActive, int people, int capacity,
                                Reservation activeReservation, Instant now) {
        return classify(maintenance, popUpActive, people, capacity, activeReservation, false, now);
    }

    /**
     * {@code attended}: someone was seen during the reservation or the holder checked in. An empty room after that
     * is a class that ended or left early, not a no-show.
     */
    public SpaceStatus classify(boolean maintenance, boolean popUpActive, int people, int capacity,
                                Reservation activeReservation, boolean attended, Instant now) {
        if (maintenance) {
            return SpaceStatus.MANTENIMIENTO;
        }
        if (popUpActive) {
            return people > capacity ? SpaceStatus.ALERTA_SOBRECUPO : SpaceStatus.HABILITADO_POPUP_PARCIALES;
        }
        if (people > capacity) {
            return SpaceStatus.ALERTA_SOBRECUPO;
        }
        if (activeReservation != null) {
            if (people == 0) {
                if (attended || activeReservation.checkedIn()) {
                    return SpaceStatus.LIBRE;
                }
                boolean graceOver = !now.isBefore(activeReservation.startsAt().plus(noShowGrace));
                return graceOver ? SpaceStatus.LIBERADO_POR_NO_SHOW : SpaceStatus.RESERVADO_EN_ESPERA;
            }
            boolean underused = people < activeReservation.expectedAttendees() * underuseRatio;
            return underused ? SpaceStatus.ALERTA_SUBUTILIZADO : SpaceStatus.OCUPADO_OPTIMO;
        }
        return people == 0 ? SpaceStatus.LIBRE : SpaceStatus.OCUPADO_OPTIMO;
    }
}
