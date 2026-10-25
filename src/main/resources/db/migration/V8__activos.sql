-- Workstation inventory: each asset (computer, chair, table) stands at a seat of a room's grid. The workstation is
-- not a table of its own: it is the assets that share space and seat number. ultimo_latido is the last heartbeat a
-- computer sent while on.
CREATE TABLE activos (
    codigo VARCHAR(40) PRIMARY KEY,
    tipo VARCHAR(8) NOT NULL CHECK (tipo IN ('CPU', 'SILLA', 'MESA')),
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    numero_puesto INTEGER NOT NULL CHECK (numero_puesto >= 1),
    potencia_w DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (potencia_w >= 0),
    fuera_de_servicio BOOLEAN NOT NULL DEFAULT FALSE,
    ultimo_latido TIMESTAMPTZ,
    CONSTRAINT chk_activo_potencia CHECK (tipo = 'CPU' OR potencia_w = 0)
);
CREATE INDEX idx_activos_puesto ON activos (space_id, numero_puesto);
