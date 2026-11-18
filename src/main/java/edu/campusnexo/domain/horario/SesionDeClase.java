package edu.campusnexo.domain.horario;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One class of the academic timetable: a group's course in a room, every week on {@code dia}, from {@code inicio} to
 * {@code fin} (whole hours). {@code asignado}: the timetable gave it no room, or put it in a room another group was
 * using, and the timetable conversion chose a free one.
 */
public record SesionDeClase(String grupo, int estudiantes, String curso, DayOfWeek dia, int inicio, int fin,
                            String salon, boolean asignado) {

    public SesionDeClase {
        requireText(grupo, "grupo");
        requireText(curso, "curso");
        requireNonNull(dia, "dia");
        requireText(salon, "salon");
        require(estudiantes >= 0 && inicio >= 0 && fin <= 24 && inicio < fin,
                "La clase debe durar al menos una hora del día y tener estudiantes no negativos");
    }

    /** Minute of the day when the class starts. */
    public int desde() {
        return inicio * 60;
    }

    /** Minute of the day when the class ends. */
    public int hasta() {
        return fin * 60;
    }

    public String etiqueta() {
        return curso + " · " + grupo;
    }

    /** Whether the class is under way at that weekday and time. */
    public boolean enCurso(DayOfWeek dia, LocalTime hora) {
        int minuto = hora.getHour() * 60 + hora.getMinute();
        return this.dia == dia && minuto >= desde() && minuto < hasta();
    }
}
