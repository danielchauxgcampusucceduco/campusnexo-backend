-- Campus catalog: branches, buildings and the monitored spaces. A space knows its floor, its seat grid (one camera
-- region per seat, NULL for rooms without individual seats), its computers and whether it has a climate unit the
-- twin can command.
CREATE TABLE campus_branches (
    branch_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    city VARCHAR(80) NOT NULL,
    climate_profile VARCHAR(24) NOT NULL CHECK (climate_profile IN ('SEDE_TROPICAL', 'SEDE_ANDINA', 'SEDE_TEMPLADA')),
    avg_outside_temp_c DOUBLE PRECISION NOT NULL,
    kwh_tariff_cop NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE buildings (
    building_id VARCHAR(32) PRIMARY KEY,
    branch_id VARCHAR(32) NOT NULL REFERENCES campus_branches (branch_id),
    name VARCHAR(120) NOT NULL,
    floors INTEGER NOT NULL DEFAULT 1 CHECK (floors > 0)
);

CREATE TABLE spaces (
    space_id VARCHAR(32) PRIMARY KEY,
    building_id VARCHAR(32) NOT NULL REFERENCES buildings (building_id),
    name VARCHAR(120) NOT NULL,
    space_type VARCHAR(24) NOT NULL CHECK (space_type IN ('AULA', 'SALA_ESTUDIO', 'AUDITORIO', 'ADMINISTRATIVA',
        'LOGISTICA', 'BIBLIOTECA', 'CAFETERIA', 'LABORATORIO', 'SALA_COMPUTO', 'ZONA_COMUN')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    area_sqm DOUBLE PRECISION NOT NULL CHECK (area_sqm > 0),
    floor INTEGER NOT NULL DEFAULT 1 CHECK (floor BETWEEN 0 AND 10),
    seat_rows INTEGER CHECK (seat_rows BETWEEN 1 AND 40),
    seat_columns INTEGER CHECK (seat_columns BETWEEN 1 AND 40),
    computers INTEGER NOT NULL DEFAULT 0 CHECK (computers >= 0),
    hvac_installed BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_spaces_building ON spaces (building_id);
