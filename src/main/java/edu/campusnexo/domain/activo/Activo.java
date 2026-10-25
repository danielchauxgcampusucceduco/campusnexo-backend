package edu.campusnexo.domain.activo;

import static edu.campusnexo.domain.shared.DomainChecks.requireNonNull;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

import java.util.Objects;

/**
 * A physical asset identified by its inventory code (the label stuck on it). Leaf of the inventory composite;
 * the concrete kinds decide their own power and how they report being in use.
 */
public abstract sealed class Activo implements ElementoDeInventario permits Cpu, Silla, Mesa {
    private final String codigo;
    private final boolean fueraDeServicio;

    protected Activo(String codigo, boolean fueraDeServicio) {
        this.codigo = requireText(codigo, "codigo", 40).strip();
        this.fueraDeServicio = fueraDeServicio;
    }

    public abstract TipoActivo tipo();

    @Override
    public String codigo() {
        return codigo;
    }

    public boolean fueraDeServicio() {
        return fueraDeServicio;
    }

    @Override
    public boolean operativo() {
        return !fueraDeServicio;
    }

    @Override
    public double potenciaNominalW() {
        return 0;
    }

    /** Builds an asset of the given kind; {@code potenciaW} only matters for a CPU. */
    public static Activo registrar(TipoActivo tipo, String codigo, double potenciaW, boolean fueraDeServicio) {
        requireNonNull(tipo, "tipo");
        return switch (tipo) {
            case CPU -> new Cpu(codigo, potenciaW, fueraDeServicio, null);
            case SILLA -> new Silla(codigo, fueraDeServicio);
            case MESA -> new Mesa(codigo, fueraDeServicio);
        };
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Activo activo && codigo.equals(activo.codigo);
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
