package edu.campusnexo.application.disponibilidad;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.port.in.CampusCatalogUseCase;
import edu.campusnexo.application.port.in.DisponibilidadUseCase;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.catalog.CampusSpace;
import edu.campusnexo.domain.horario.SesionDeClase;
import edu.campusnexo.domain.port.HorarioAcademicoRepository;
import edu.campusnexo.domain.port.ReservationRepository;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.reservation.Reservation;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Availability of the student spaces from three sources: the academic timetable (classes every week), the bookings
 * (one-off) and the twin (who the camera sees now). A slot is LIBRE when none of them holds it; the current slot is
 * OCUPADO when the camera sees someone, and PROGRAMADO_SIN_USO when a class or booking is under way, its grace period
 * passed and the camera sees nobody: that room is free in practice, and the administration is told.
 *
 * <p>Read with one query per kind of data (spaces, twins, the day's bookings), like the campus view.
 */
public class DisponibilidadService implements DisponibilidadUseCase {
    static final LocalTime ABRE = LocalTime.of(7, 0);
    static final LocalTime CIERRA = LocalTime.of(22, 0);

    private final Clock clock;
    private final CampusCatalogUseCase catalog;
    private final HorarioAcademicoRepository horario;
    private final ReservationRepository reservations;
    private final SpaceRepositoryPort twins;
    private final AlertRecorder alerts;
    private final ZoneId zone;
    private final Duration grace;
    private final Duration cooldown;

    /**
     * @param grace    how long after a class or booking starts an empty room counts as unused (the no-show grace)
     * @param cooldown how long a room's alert is not repeated
     */
    public DisponibilidadService(Clock clock, CampusCatalogUseCase catalog, HorarioAcademicoRepository horario,
                                 ReservationRepository reservations, SpaceRepositoryPort twins, AlertRecorder alerts,
                                 ZoneId zone, Duration grace, Duration cooldown) {
        this.clock = clock;
        this.catalog = catalog;
        this.horario = horario;
        this.reservations = reservations;
        this.twins = twins;
        this.alerts = alerts;
        this.zone = zone;
        this.grace = grace;
        this.cooldown = cooldown;
    }

    @Override
    public List<SesionDeClase> horario() {
        return horario.sesiones();
    }

    @Override
    public DisponibilidadDelCampus disponibilidad(LocalDate fecha) {
        Instant now = clock.instant();
        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        boolean today = local.toLocalDate().equals(fecha);
        Map<String, SpaceTwinState> states = twins.findAllTwins().stream()
                .collect(Collectors.toMap(SpaceTwinState::spaceId, Function.identity()));
        Map<String, List<Reservation>> booked = reservations.findOverlappingForAll(instant(fecha, ABRE),
                instant(fecha, CIERRA)).stream().collect(Collectors.groupingBy(Reservation::spaceId));
        Map<String, List<SesionDeClase>> classes = horario.sesiones().stream()
                .filter(s -> s.dia() == fecha.getDayOfWeek())
                .collect(Collectors.groupingBy(SesionDeClase::salon));

        List<EspacioDisponible> espacios = catalog.listSpaces(null).stream()
                .filter(CampusSpace::accesibleParaEstudiantes)
                .map(space -> espacio(space, fecha, today ? local.toLocalTime() : null,
                        classes.getOrDefault(space.spaceId(), List.of()),
                        booked.getOrDefault(space.spaceId(), List.of()), states.get(space.spaceId())))
                .toList();
        return new DisponibilidadDelCampus(fecha, now, ABRE, CIERRA, espacios);
    }

    @Override
    public List<String> vigilarHorario() {
        Instant now = clock.instant();
        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        Map<String, SpaceTwinState> states = twins.findAllTwins().stream()
                .collect(Collectors.toMap(SpaceTwinState::spaceId, Function.identity()));
        List<String> alerted = new ArrayList<>();
        for (SesionDeClase sesion : horario.sesiones()) {
            SpaceTwinState state = states.get(sesion.salon());
            if (!sesion.enCurso(local.getDayOfWeek(), local.toLocalTime()) || state == null) {
                continue;
            }
            boolean gracePassed = !local.toLocalTime().isBefore(LocalTime.of(sesion.inicio(), 0).plus(grace));
            // Without a camera the room cannot be judged; under maintenance it is not on offer anyway.
            boolean empty = state.sensing().reporting() && state.occupancy().people() == 0 && !state.maintenance();
            if (gracePassed && empty && alerts.recordUnlessRaisedSince(Alert.raise(sesion.salon(),
                    AlertType.HORARIO_SIN_USO, AlertSeverity.INFORMATIVA, now,
                    "Programado para " + sesion.etiqueta() + " de " + hora(sesion.inicio()) + " a " + hora(sesion.fin())
                            + " y nadie llegó: el salón está libre", 0, sesion.estudiantes()), now.minus(cooldown))) {
                alerted.add(sesion.salon());
            }
        }
        return alerted;
    }

    private EspacioDisponible espacio(CampusSpace space, LocalDate fecha, LocalTime now, List<SesionDeClase> classes,
                                      List<Reservation> booked, SpaceTwinState state) {
        boolean camera = state != null && state.sensing().reporting();
        int people = camera ? state.occupancy().people() : 0;
        List<FranjaDisponible> franjas = new ArrayList<>();
        EstadoFranja ahora = null;
        for (LocalTime desde = ABRE; desde.isBefore(CIERRA); desde = desde.plusHours(1)) {
            LocalTime hasta = desde.plusHours(1);
            FranjaDisponible franja = planned(fecha, desde, hasta, classes, booked);
            if (now != null && !now.isBefore(desde) && now.isBefore(hasta)) {
                franja = current(franja, fecha, now, classes, booked, camera, people);
                ahora = franja.estado();
            }
            franjas.add(franja);
        }
        if (now != null && ahora == null) {
            // Outside the campus hours only the camera says anything.
            ahora = people > 0 ? EstadoFranja.OCUPADO : EstadoFranja.LIBRE;
        }
        boolean free = ahora == EstadoFranja.LIBRE || ahora == EstadoFranja.PROGRAMADO_SIN_USO;
        LocalTime libreHasta = free ? nextUse(fecha, now, classes, booked) : null;
        return new EspacioDisponible(space.spaceId(), space.name(), space.type(), space.buildingName(), space.floor(),
                space.capacity(), camera, people, ahora, libreHasta, List.copyOf(franjas));
    }

    /** The slot as the timetable and the bookings plan it. */
    private FranjaDisponible planned(LocalDate fecha, LocalTime desde, LocalTime hasta, List<SesionDeClase> classes,
                                     List<Reservation> booked) {
        int from = desde.getHour() * 60;
        int to = from + 60;
        for (SesionDeClase sesion : classes) {
            if (sesion.desde() < to && from < sesion.hasta()) {
                return new FranjaDisponible(desde, hasta, EstadoFranja.CLASE, sesion.etiqueta());
            }
        }
        Instant start = instant(fecha, desde);
        Instant end = instant(fecha, hasta);
        for (Reservation reservation : booked) {
            if (reservation.startsAt().isBefore(end) && reservation.endsAt().isAfter(start)) {
                return new FranjaDisponible(desde, hasta, EstadoFranja.RESERVADO, reservation.reference());
            }
        }
        return new FranjaDisponible(desde, hasta, EstadoFranja.LIBRE, null);
    }

    /** The current slot, with what the camera sees: someone in it, or a class or booking nobody came to. */
    private FranjaDisponible current(FranjaDisponible planned, LocalDate fecha, LocalTime now,
                                     List<SesionDeClase> classes, List<Reservation> booked, boolean camera, int people) {
        if (people > 0) {
            String detalle = people + (people == 1 ? " persona" : " personas")
                    + (planned.detalle() == null ? "" : " · " + planned.detalle());
            return new FranjaDisponible(planned.desde(), planned.hasta(), EstadoFranja.OCUPADO, detalle);
        }
        if (!camera || planned.estado() == EstadoFranja.LIBRE) {
            return planned;
        }
        Instant nowInstant = instant(fecha, now);
        int minute = now.getHour() * 60 + now.getMinute();
        boolean started = classes.stream().anyMatch(s -> minute >= s.desde() + grace.toMinutes() && minute < s.hasta())
                || booked.stream().anyMatch(r -> !nowInstant.isBefore(r.startsAt().plus(grace)) && nowInstant.isBefore(r.endsAt()));
        return started ? new FranjaDisponible(planned.desde(), planned.hasta(), EstadoFranja.PROGRAMADO_SIN_USO,
                "Programado para " + planned.detalle() + " y nadie llegó") : planned;
    }

    /** When the room is next used on that day: the next class or booking, or the closing time. */
    private LocalTime nextUse(LocalDate fecha, LocalTime now, List<SesionDeClase> classes, List<Reservation> booked) {
        int minute = now.getHour() * 60 + now.getMinute();
        LocalTime next = CIERRA;
        for (SesionDeClase sesion : classes) {
            if (sesion.desde() > minute) {
                LocalTime start = LocalTime.of(sesion.inicio(), 0);
                next = start.isBefore(next) ? start : next;
            }
        }
        Instant nowInstant = instant(fecha, now);
        for (Reservation reservation : booked) {
            if (reservation.startsAt().isAfter(nowInstant)) {
                LocalTime start = LocalDateTime.ofInstant(reservation.startsAt(), zone).toLocalTime();
                next = start.isBefore(next) ? start : next;
            }
        }
        return next;
    }

    private Instant instant(LocalDate fecha, LocalTime hora) {
        return fecha.atTime(hora).atZone(zone).toInstant();
    }

    private static String hora(int hora) {
        return String.format("%02d:00", hora);
    }
}
