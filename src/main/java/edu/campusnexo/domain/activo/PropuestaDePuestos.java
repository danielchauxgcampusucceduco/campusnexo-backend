package edu.campusnexo.domain.activo;

import static edu.campusnexo.domain.shared.DomainChecks.require;
import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import edu.campusnexo.domain.catalog.SeatLayout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the camera proposes to register in a room: the workstations it found, each with its furniture and, once
 * paired, its computer. Nothing is registered until an administrator confirms it, and a new run only proposes
 * what the inventory still lacks ({@link #altas}), so confirming twice adds nothing.
 */
public record PropuestaDePuestos(String spaceId, Instant recibidaAt, boolean emulada, List<PuestoPropuesto> puestos) {
    public PropuestaDePuestos {
        requireText(spaceId, "spaceId");
        requireNonNull(recibidaAt, "recibidaAt");
        puestos = List.copyOf(requireNonNull(puestos, "puestos"));
        require(!puestos.isEmpty(), "La propuesta no tiene puestos");
        require(puestos.stream().map(PuestoPropuesto::etiqueta).distinct().count() == puestos.size(),
                "Dos puestos propuestos ocupan la misma fila y columna");
        require(puestos.stream().filter(p -> p.cpu() != null).map(PuestoPropuesto::cpu).distinct().count()
                == puestos.stream().filter(p -> p.cpu() != null).count(), "Un computador quedó en dos puestos");
    }

    /** Assets to add at one seat: what the camera found there and the inventory lacks. */
    public record Alta(String etiqueta, int numero, Set<TipoActivo> agregar, String cpu) {
        public Alta {
            agregar = Set.copyOf(agregar);
        }
    }

    /** Checks that every proposed seat fits the room's grid; the camera may not invent rows the room lacks. */
    public void validarRejilla(SeatLayout rejilla) {
        require(rejilla != null, "El espacio " + spaceId + " no tiene rejilla de puestos");
        for (PuestoPropuesto puesto : puestos) {
            require(puesto.fila() <= rejilla.rows() && puesto.columna() <= rejilla.columns(),
                    "El puesto " + puesto.etiqueta() + " no cabe en la rejilla de " + rejilla.rows() + " × "
                            + rejilla.columns() + " de " + spaceId);
        }
    }

    public List<PuestoPropuesto> incompletos() {
        return puestos.stream().filter(PuestoPropuesto::incompleto).toList();
    }

    /** The same proposal with the computer paired to the seat at that row and column. */
    public PropuestaDePuestos conCpu(int fila, int columna, String cpu) {
        require(puestos.stream().anyMatch(p -> p.fila() == fila && p.columna() == columna),
                "No hay un puesto propuesto en la fila " + fila + ", columna " + columna);
        return new PropuestaDePuestos(spaceId, recibidaAt, emulada, puestos.stream()
                .map(p -> p.fila() == fila && p.columna() == columna ? p.conCpu(cpu)
                        : cpu.equals(p.cpu()) ? p.conCpu(null) : p)
                .toList());
    }

    /** Per seat, the furniture and computer the camera found and the inventory does not have yet. */
    public List<Alta> altas(SeatLayout rejilla, List<PuestoDeTrabajo> actuales) {
        Map<Integer, PuestoDeTrabajo> porNumero = actuales.stream()
                .collect(Collectors.toMap(PuestoDeTrabajo::numero, Function.identity()));
        List<Alta> altas = new ArrayList<>();
        for (PuestoPropuesto puesto : puestos) {
            int numero = (puesto.fila() - 1) * rejilla.columns() + puesto.columna();
            Set<TipoActivo> presentes = EnumSet.noneOf(TipoActivo.class);
            PuestoDeTrabajo actual = porNumero.get(numero);
            if (actual != null) {
                actual.elementos().stream().filter(Activo.class::isInstance)
                        .forEach(e -> presentes.add(((Activo) e).tipo()));
            }
            Set<TipoActivo> agregar = EnumSet.noneOf(TipoActivo.class);
            if (puesto.mesa() && !presentes.contains(TipoActivo.MESA)) {
                agregar.add(TipoActivo.MESA);
            }
            if (puesto.silla() && !presentes.contains(TipoActivo.SILLA)) {
                agregar.add(TipoActivo.SILLA);
            }
            if (puesto.cpu() != null && !presentes.contains(TipoActivo.CPU)) {
                agregar.add(TipoActivo.CPU);
            }
            if (!agregar.isEmpty()) {
                altas.add(new Alta(puesto.etiqueta(), numero, agregar, puesto.cpu()));
            }
        }
        return altas;
    }

    /** Inventory code of furniture registered from the camera, as the inventory office labels it (301A-SILLA-07). */
    public static String codigoDe(String spaceId, TipoActivo tipo, int numero) {
        return String.format(Locale.ROOT, "%s-%s-%02d", spaceId.replace("-", ""), tipo, numero);
    }
}
