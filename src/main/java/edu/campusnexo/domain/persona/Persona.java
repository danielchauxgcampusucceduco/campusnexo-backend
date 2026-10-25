package edu.campusnexo.domain.persona;

import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.SpaceType;
import java.time.Duration;
import java.util.Objects;

/**
 * A member of the university the administration registers so bookings and check-ins name who is responsible.
 * Only the institutional code (the one printed on the card) and the name are kept: no photos, no biometrics,
 * no contact data (Ley 1581 de 2012). People are recognized by that code at the room's kiosk; the camera only
 * counts them.
 *
 * <p>Each kind of person answers the same questions differently ({@link #puedeReservar}, {@link #reservaMaxima}),
 * so the booking rules ask the person instead of switching on its type.
 */
public abstract sealed class Persona permits Docente, Estudiante {
    private final String codigo;
    private final String nombre;

    protected Persona(String codigo, String nombre) {
        this.codigo = requireText(codigo, "codigo", 32).strip();
        this.nombre = requireText(nombre, "nombre", 120).strip();
    }

    public String codigo() {
        return codigo;
    }

    public String nombre() {
        return nombre;
    }

    public abstract TipoPersona tipo();

    /** Free text of the role: the department of a teacher, the program of a student. */
    public abstract String adscripcion();

    /** Whether this person may book a space of this type in their own name. */
    public abstract boolean puedeReservar(SpaceType tipoDeEspacio);

    /** Longest booking this person may hold. */
    public abstract Duration reservaMaxima();

    /** Builds the person of the given kind; {@code adscripcion} is the department or the program. */
    public static Persona registrar(TipoPersona tipo, String codigo, String nombre, String adscripcion) {
        return switch (tipo) {
            case DOCENTE -> new Docente(codigo, nombre, adscripcion);
            case ESTUDIANTE -> new Estudiante(codigo, nombre, adscripcion);
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Persona persona && codigo.equals(persona.codigo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigo);
    }

    @Override
    public String toString() {
        return tipo() + " " + codigo;
    }
}
