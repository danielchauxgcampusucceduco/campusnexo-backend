package edu.campusnexo.domain.activo;

/**
 * Component of the inventory (Composite pattern): a single asset ({@link Activo}) or a workstation that groups
 * several ({@link PuestoDeTrabajo}). Callers add up power or check operability without asking which one they hold.
 */
public sealed interface ElementoDeInventario permits Activo, PuestoDeTrabajo {
    String codigo();

    /** Power drawn when everything in it is on, in watts; furniture draws none. */
    double potenciaNominalW();

    /** Whether it can be used: nothing in it is out of service. */
    boolean operativo();
}
