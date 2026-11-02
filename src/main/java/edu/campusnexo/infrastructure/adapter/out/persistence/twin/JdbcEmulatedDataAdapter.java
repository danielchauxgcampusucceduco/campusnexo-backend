package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import edu.campusnexo.application.port.out.EmulatedDataPort;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter of {@link EmulatedDataPort}: plain deletes, inside the caller's transaction. */
@Repository
class JdbcEmulatedDataAdapter implements EmulatedDataPort {
    private static final List<String> DELETES = List.of(
            "DELETE FROM telemetry_stream WHERE space_id = :space",
            "DELETE FROM occupancy_estimates WHERE space_id = :space",
            "DELETE FROM climate_recommendations WHERE space_id = :space",
            "DELETE FROM alerts WHERE space_id = :space",
            "DELETE FROM propuestas_puestos WHERE space_id = :space AND emulada");

    private final JdbcClient jdbc;

    JdbcEmulatedDataAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void forget(String spaceId) {
        DELETES.forEach(sql -> jdbc.sql(sql).param("space", spaceId).update());
    }
}
