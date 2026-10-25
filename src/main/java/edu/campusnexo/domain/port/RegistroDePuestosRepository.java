package edu.campusnexo.domain.port;

import edu.campusnexo.domain.activo.EmparejamientoCpu;
import edu.campusnexo.domain.activo.PropuestaDePuestos;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** The pending proposal of each room, its pairing schedule and the computer agents that are not in a seat yet. */
public interface RegistroDePuestosRepository {
    /** Replaces the pending proposal of the room. */
    void savePropuesta(PropuestaDePuestos propuesta);

    Optional<PropuestaDePuestos> findPropuesta(String spaceId);

    void deletePropuesta(String spaceId);

    void saveEmparejamiento(EmparejamientoCpu emparejamiento);

    Optional<EmparejamientoCpu> findEmparejamiento(String spaceId);

    /** Records that the agent of computer {@code cpu}, installed in the room, is alive. */
    void saveAgente(String cpu, String spaceId, Instant at);

    /** Computers whose agent reported from the room, ordered by code. */
    List<String> findAgentes(String spaceId);
}
