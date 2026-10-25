package edu.campusnexo.domain.persona;

import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.SpaceType;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/** A teacher: books the spaces where classes are taught, for up to a long session. */
public final class Docente extends Persona {
    static final Set<SpaceType> RESERVABLES = EnumSet.of(SpaceType.AULA, SpaceType.SALA_COMPUTO,
            SpaceType.LABORATORIO, SpaceType.AUDITORIO, SpaceType.SALA_ESTUDIO);
    static final Duration MAXIMA = Duration.ofHours(4);

    private final String departamento;

    public Docente(String codigo, String nombre, String departamento) {
        super(codigo, nombre);
        this.departamento = requireText(departamento, "departamento", 120).strip();
    }

    @Override
    public TipoPersona tipo() {
        return TipoPersona.DOCENTE;
    }

    @Override
    public String adscripcion() {
        return departamento;
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
