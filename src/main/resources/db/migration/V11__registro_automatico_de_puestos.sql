-- Registration of workstations from the camera. The edge device proposes the seats it found in the empty room
-- (one pending proposal per room, as a JSON list of seats); computers are paired with their seats by showing a
-- pattern in their own time slot; an administrator confirms and only then the inventory changes.
CREATE TABLE propuestas_puestos (
    space_id VARCHAR(32) PRIMARY KEY REFERENCES spaces (space_id),
    recibida_at TIMESTAMPTZ NOT NULL,
    emulada BOOLEAN NOT NULL,
    puestos TEXT NOT NULL
);

CREATE TABLE emparejamientos_cpu (
    space_id VARCHAR(32) PRIMARY KEY REFERENCES spaces (space_id),
    inicio TIMESTAMPTZ NOT NULL,
    ranura_ms BIGINT NOT NULL CHECK (ranura_ms > 0),
    cpus TEXT NOT NULL
);

-- Agents of computers that reported from a room and are not in a seat yet.
CREATE TABLE agentes_cpu (
    cpu VARCHAR(40) PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    ultimo_latido TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_agentes_space ON agentes_cpu (space_id, cpu);
