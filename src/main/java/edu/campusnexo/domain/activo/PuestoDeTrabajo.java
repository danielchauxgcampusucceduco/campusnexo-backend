package edu.campusnexo.domain.activo;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.vision.SeatGrid;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A workstation: the table, chair and, in a computer room, the computer at one seat of the room's grid
 * (Composite pattern). It answers the same questions as a single asset by asking its parts, and its code is
 * the seat's camera region ({@link SeatGrid#zoneIdOf}), so the camera's view of the seat and the inventory
 * meet on the same key.
 */
public final class PuestoDeTrabajo implements ElementoDeInventario {
    private final String spaceId;
    private final int numero;
    private final List<ElementoDeInventario> elementos;

    public PuestoDeTrabajo(String spaceId, int numero, List<? extends ElementoDeInventario> elementos) {
        this.spaceId = requireText(spaceId, "spaceId");
        this.numero = requireRange(numero, 1, 1_000, "numero");
        requireNonNull(elementos, "elementos");
        this.elementos = List.copyOf(elementos);
        require(this.elementos.stream().map(ElementoDeInventario::codigo).distinct().count() == this.elementos.size(),
                "Un puesto no puede tener dos veces el mismo activo");
    }

    public String spaceId() {
        return spaceId;
    }

    public int numero() {
        return numero;
    }

    public List<ElementoDeInventario> elementos() {
        return elementos;
    }

    @Override
    public String codigo() {
        return SeatGrid.zoneIdOf(spaceId, numero);
    }

    @Override
    public double potenciaNominalW() {
        return elementos.stream().mapToDouble(ElementoDeInventario::potenciaNominalW).sum();
    }

    /** Usable when every part works and it has at least a table and a chair. */
    @Override
    public boolean operativo() {
        return faltantes(false).isEmpty() && elementos.stream().allMatch(ElementoDeInventario::operativo);
    }

    public Optional<Cpu> cpu() {
        return elementos.stream().filter(Cpu.class::isInstance).map(Cpu.class::cast).findFirst();
    }

    /** Kinds of asset the workstation should have and lacks; a computer is expected only where asked. */
    public Set<TipoActivo> faltantes(boolean requiereCpu) {
        Set<TipoActivo> esperados = requiereCpu ? EnumSet.allOf(TipoActivo.class)
                : EnumSet.of(TipoActivo.SILLA, TipoActivo.MESA);
        elementos.stream().filter(Activo.class::isInstance).map(e -> ((Activo) e).tipo()).forEach(esperados::remove);
        return esperados;
    }

    /**
     * The computer is on and nobody sits at the seat: the case the administration wants to catch, since a
     * computer left on after class wastes energy and is an open session. {@code ocupado} comes from the camera.
     */
    public boolean cpuEncendidaSinUsuario(boolean ocupado, Instant ahora, Duration vigenciaLatido) {
        return !ocupado && cpu().map(c -> c.encendida(ahora, vigenciaLatido)).orElse(false);
    }

    /** The same workstation with the computer's newer heartbeat. */
    public PuestoDeTrabajo conLatido(Instant at) {
        return new PuestoDeTrabajo(spaceId, numero,
                elementos.stream().map(e -> e instanceof Cpu c ? c.conLatido(at) : e).toList());
    }
}
