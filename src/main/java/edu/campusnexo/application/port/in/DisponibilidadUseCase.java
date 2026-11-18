package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.catalog.SpaceType;
import edu.campusnexo.domain.horario.SesionDeClase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Which student spaces are free, and when: the academic timetable, the bookings and what the camera sees now, hour by
 * hour. The administration is told of the classes nobody attends, so the room can be offered to someone else.
 */
public interface DisponibilidadUseCase {
    /** The academic timetable of the campus. */
    List<SesionDeClase> horario();

    /** Availability of every student space on {@code fecha} (campus time zone), in one-hour slots. */
    DisponibilidadDelCampus disponibilidad(LocalDate fecha);

    /**
     * Checks the classes under way: a scheduled room whose camera sees nobody once the grace period passed gets a
     * {@code HORARIO_SIN_USO} alert. Returns the rooms that got one now.
     */
    List<String> vigilarHorario();

    /** What a slot holds; {@code PROGRAMADO_SIN_USO}: a class or booking under way with nobody in the room. */
    enum EstadoFranja { LIBRE, CLASE, RESERVADO, OCUPADO, PROGRAMADO_SIN_USO }

    record FranjaDisponible(LocalTime desde, LocalTime hasta, EstadoFranja estado, String detalle) {
    }

    /**
     * One student space. {@code conCamara}: its camera has reported, so {@code personas} is what it sees; without it
     * the slots only follow the timetable and the bookings. {@code ahora} and {@code libreHasta} (until when it stays
     * free, the end of the day if nothing else is scheduled) are {@code null} on another day or while it is in use.
     */
    record EspacioDisponible(String spaceId, String nombre, SpaceType tipo, String edificio, int piso, int aforo,
                             boolean conCamara, int personas, EstadoFranja ahora, LocalTime libreHasta,
                             List<FranjaDisponible> franjas) {
    }

    record DisponibilidadDelCampus(LocalDate fecha, Instant generadoEn, LocalTime abre, LocalTime cierra,
                                   List<EspacioDisponible> espacios) {
    }
}
