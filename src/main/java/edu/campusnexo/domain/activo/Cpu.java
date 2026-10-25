package edu.campusnexo.domain.activo;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;

import java.time.Duration;
import java.time.Instant;

/**
 * A desktop computer. The camera cannot see the tower under the table, so the computer reports itself: a small
 * agent sends a heartbeat every minute while it is on. {@code ultimoLatido} is the last one received, or
 * {@code null} if it never reported.
 */
public final class Cpu extends Activo {
    private final double potenciaW;
    private final Instant ultimoLatido;

    public Cpu(String codigo, double potenciaW, boolean fueraDeServicio, Instant ultimoLatido) {
        super(codigo, fueraDeServicio);
        this.potenciaW = requireRange(potenciaW, 1, 2_000, "potenciaW");
        this.ultimoLatido = ultimoLatido;
    }

    @Override
    public TipoActivo tipo() {
        return TipoActivo.CPU;
    }

    @Override
    public double potenciaNominalW() {
        return potenciaW;
    }

    public Instant ultimoLatido() {
        return ultimoLatido;
    }

    /** On when it reported within {@code vigencia}; a computer that stops reporting is taken as off. */
    public boolean encendida(Instant ahora, Duration vigencia) {
        requireNonNull(ahora, "ahora");
        return ultimoLatido != null && !ultimoLatido.isAfter(ahora) && !ultimoLatido.isBefore(ahora.minus(vigencia));
    }

    /** Keeps the newest heartbeat, so one that arrives late never makes the computer look off. */
    public Cpu conLatido(Instant at) {
        requireNonNull(at, "at");
        if (ultimoLatido != null && !at.isAfter(ultimoLatido)) {
            return this;
        }
        return new Cpu(codigo(), potenciaW, fueraDeServicio(), at);
    }
}
