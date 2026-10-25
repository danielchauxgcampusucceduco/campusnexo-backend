package edu.campusnexo.domain.persona;

import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.SpaceType;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/** A student: books only study spaces, for short sessions, so tables do not stay held for a whole day. */
public final class Estudiante extends Persona {
    static final Set<SpaceType> RESERVABLES = EnumSet.of(SpaceType.SALA_ESTUDIO, SpaceType.BIBLIOTECA);
    static final Duration MAXIMA = Duration.ofHours(2);

    private final String programa;

    public Estudiante(String codigo, String nombre, String programa) {
        super(codigo, nombre);
        this.programa = requireText(programa, "programa", 120).strip();
    }

    @Override
    public TipoPersona tipo() {
        return TipoPersona.ESTUDIANTE;
    }

    @Override
    public String adscripcion() {
        return programa;
    }

    @Override
    public boolean puedeReservar(SpaceType tipoDeEspacio) {
        return RESERVABLES.contains(tipoDeEspacio);
    }

    @Override
    public Duration reservaMaxima() {
        return MAXIMA;
    }
}
