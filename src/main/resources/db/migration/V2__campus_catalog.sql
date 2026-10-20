CREATE TABLE campus_branches (
    branch_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    city VARCHAR(80) NOT NULL,
    climate_profile VARCHAR(24) NOT NULL CHECK (climate_profile IN ('SEDE_TROPICAL', 'SEDE_ANDINA', 'SEDE_TEMPLADA')),
    avg_outside_temp_c DOUBLE PRECISION NOT NULL,
    kwh_tariff_cop NUMERIC(10,2) NOT NULL DEFAULT 890.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE buildings (
    building_id VARCHAR(32) PRIMARY KEY,
    branch_id VARCHAR(32) NOT NULL REFERENCES campus_branches(branch_id),
    name VARCHAR(120) NOT NULL,
    floors INTEGER NOT NULL DEFAULT 1 CHECK (floors > 0)
);

CREATE TABLE spaces (
    space_id VARCHAR(32) PRIMARY KEY,
    building_id VARCHAR(32) NOT NULL REFERENCES buildings(building_id),
    name VARCHAR(120) NOT NULL,
    space_type VARCHAR(24) NOT NULL CHECK (space_type IN ('AULA', 'BIBLIOTECA', 'LABORATORIO', 'AUDITORIO', 'ZONA_COMUN')),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    area_sqm DOUBLE PRECISION NOT NULL CHECK (area_sqm > 0)
);
CREATE INDEX idx_spaces_building ON spaces (building_id);

INSERT INTO campus_branches (branch_id, name, city, climate_profile, avg_outside_temp_c, kwh_tariff_cop) VALUES
('SEDE-ANDINA', 'Campus Torobajo / Sede Andina', 'Pasto', 'SEDE_ANDINA', 14.5, 865.00),
('SEDE-TROPICAL', 'Campus Metropolitano Norte', 'Barranquilla', 'SEDE_TROPICAL', 31.8, 1020.00);

INSERT INTO buildings (building_id, branch_id, name, floors) VALUES
('BLQ-ING-SUR', 'SEDE-ANDINA', 'Bloque C - Ingeniería y Software', 4),
('BLQ-BIB-SUR', 'SEDE-ANDINA', 'Biblioteca Central Universitaria', 3),
('BLQ-AUL-NORTE', 'SEDE-TROPICAL', 'Edificio de Aulas Magnas', 5);

INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm) VALUES
('AULA-301', 'BLQ-ING-SUR', 'Aula 301', 'AULA', 32, 48.00),
('AULA-MAGNA-204', 'BLQ-AUL-NORTE', 'Aula Magna 204', 'AULA', 48, 72.00),
('BIB-PISO-2', 'BLQ-BIB-SUR', 'Biblioteca - Piso 2', 'BIBLIOTECA', 120, 210.00),
('LAB-IA-102', 'BLQ-ING-SUR', 'Laboratorio IA 102', 'LABORATORIO', 24, 54.00);

