package edu.campusnexo.domain.port;

import edu.campusnexo.domain.activo.Activo;
import edu.campusnexo.domain.activo.PuestoDeTrabajo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ActivoRepository {
    /** Places the asset at seat {@code numeroPuesto} of the space, replacing the one with the same code. */
    void save(String spaceId, int numeroPuesto, Activo activo);

    /** Workstations of the space with their assets, ordered by seat number. */
    List<PuestoDeTrabajo> findPuestos(String spaceId);

    /** The workstation whose computer has this inventory code. */
    Optional<PuestoDeTrabajo> findPuestoByCpu(String codigoCpu);

    /** Stores the heartbeat unless a newer one is already stored. */
    void saveLatido(String codigoCpu, Instant at);

    /** @return whether an asset with that code existed */
    boolean delete(String codigo);
}
