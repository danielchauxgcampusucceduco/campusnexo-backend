package edu.campusnexo.domain.activo;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Pairing of the computers of a room with their seats. Each unpaired computer gets its own time slot, one after
 * the other from {@code inicio}, to show a full-screen pattern on its monitor; the edge device sees which monitor
 * changed and when, and the slot of that instant names the computer. A schedule instead of live commands: every
 * agent learns its slot in the answer to its heartbeat, so nothing else has to reach the computers.
 */
public record EmparejamientoCpu(String spaceId, Instant inicio, Duration ranura, List<String> cpus) {
    public EmparejamientoCpu {
        requireText(spaceId, "spaceId");
        requireNonNull(inicio, "inicio");
        requireNonNull(ranura, "ranura");
        require(!ranura.isNegative() && !ranura.isZero(), "La ranura debe ser positiva");
        cpus = List.copyOf(requireNonNull(cpus, "cpus"));
        require(cpus.stream().distinct().count() == cpus.size(), "Un computador aparece dos veces");
    }

    /** When a computer shows its pattern: [desde, hasta). */
    public record Ventana(Instant desde, Instant hasta) {
    }

    public Optional<Ventana> ventanaDe(String cpu) {
        int posicion = cpus.indexOf(cpu);
        if (posicion < 0) {
            return Optional.empty();
        }
        Instant desde = inicio.plus(ranura.multipliedBy(posicion));
        return Optional.of(new Ventana(desde, desde.plus(ranura)));
    }

    /** The computer whose slot contains {@code at}, if any. */
    public Optional<String> cpuMostrando(Instant at) {
        if (at.isBefore(inicio) || !at.isBefore(fin())) {
            return Optional.empty();
        }
        return Optional.of(cpus.get((int) (Duration.between(inicio, at).toMillis() / ranura.toMillis())));
    }

    public Instant fin() {
        return inicio.plus(ranura.multipliedBy(cpus.size()));
    }
}
