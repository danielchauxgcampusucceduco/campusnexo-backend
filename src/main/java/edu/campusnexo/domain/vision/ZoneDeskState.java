package edu.campusnexo.domain.vision;

public enum ZoneDeskState {
    /** No person and no belongings inside the desk polygon. */
    DISPONIBLE,
    /** A person is inside the desk polygon. */
    OCUPADO_VERIFICADO,
    /** Belongings without a person; the abandonment timer is running. */
    ALERTA_ACAPARAMIENTO,
    /** Belongings without a person for longer than the abandonment threshold: the seat is released. */
    LIBERADO_POR_ABANDONO
}
