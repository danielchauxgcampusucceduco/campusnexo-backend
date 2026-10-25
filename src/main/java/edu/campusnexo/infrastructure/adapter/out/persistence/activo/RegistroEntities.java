package edu.campusnexo.infrastructure.adapter.out.persistence.activo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Rows of the workstation registration: the pending proposal, the pairing schedule and the unpaired agents. */
final class RegistroEntities {
    private RegistroEntities() {
    }

    @Entity
    @Table(name = "propuestas_puestos")
    static class PropuestaEntity {
        @Id
        @Column(name = "space_id", length = 32)
        String spaceId;

        @Column(name = "recibida_at", nullable = false)
        Instant recibidaAt;

        @Column(name = "emulada", nullable = false)
        boolean emulada;

        @Column(name = "puestos", nullable = false, columnDefinition = "TEXT")
        String puestos;
    }

    @Entity
    @Table(name = "emparejamientos_cpu")
    static class EmparejamientoEntity {
        @Id
        @Column(name = "space_id", length = 32)
        String spaceId;

        @Column(name = "inicio", nullable = false)
        Instant inicio;

        @Column(name = "ranura_ms", nullable = false)
        long ranuraMs;

        @Column(name = "cpus", nullable = false, columnDefinition = "TEXT")
        String cpus;
    }

    @Entity
    @Table(name = "agentes_cpu")
    static class AgenteEntity {
        @Id
        @Column(name = "cpu", length = 40)
        String cpu;

        @Column(name = "space_id", nullable = false, length = 32)
        String spaceId;

        @Column(name = "ultimo_latido", nullable = false)
        Instant ultimoLatido;
    }
}
