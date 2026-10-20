-- One campus replaces the three demo branches: Universidad Cooperativa de Colombia, campus Pasto
-- (Calle 18 # 47-150, Torobajo). Block A is the old building (classrooms with individual desks, study
-- rooms, an auditorium, administration and internal logistics); block B is the new one (library,
-- cafeteria, laboratories, an auditorium and computer rooms). Codes follow the doors: 201-A is room 01 on
-- the second floor of block A. The room distribution is an illustrative model for the demo, not the
-- official inventory of the campus.

-- 1. Rooms now know their floor, their seat grid (one camera region per seat) and their computers.
ALTER TABLE spaces ADD COLUMN floor INTEGER NOT NULL DEFAULT 1;
ALTER TABLE spaces ADD COLUMN seat_rows INTEGER CHECK (seat_rows BETWEEN 1 AND 40);
ALTER TABLE spaces ADD COLUMN seat_columns INTEGER CHECK (seat_columns BETWEEN 1 AND 40);
ALTER TABLE spaces ADD COLUMN computers INTEGER NOT NULL DEFAULT 0 CHECK (computers >= 0);
ALTER TABLE spaces DROP CONSTRAINT IF EXISTS spaces_space_type_check;
ALTER TABLE spaces ADD CONSTRAINT spaces_space_type_check CHECK (space_type IN ('AULA', 'SALA_ESTUDIO',
    'AUDITORIO', 'ADMINISTRATIVA', 'LOGISTICA', 'BIBLIOTECA', 'CAFETERIA', 'LABORATORIO', 'SALA_COMPUTO',
    'ZONA_COMUN'));

-- 2. Remove the demo branches and everything recorded for their spaces.
CREATE TEMPORARY TABLE old_spaces AS
    SELECT s.space_id FROM spaces s JOIN buildings b ON b.building_id = s.building_id
    WHERE b.branch_id IN ('SEDE-ANDINA', 'SEDE-TROPICAL', 'SEDE-TEMPLADA');
DELETE FROM study_zone_rois WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM space_twin_states WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM reservations WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM alerts WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM occupancy_estimates WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM climate_recommendations WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM telemetry_stream WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM spaces WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM buildings WHERE branch_id IN ('SEDE-ANDINA', 'SEDE-TROPICAL', 'SEDE-TEMPLADA');
DELETE FROM campus_branches WHERE branch_id IN ('SEDE-ANDINA', 'SEDE-TROPICAL', 'SEDE-TEMPLADA');
DROP TABLE old_spaces;

-- 3. The campus, its two blocks and the 50 rooms.
INSERT INTO campus_branches (branch_id, name, city, climate_profile, avg_outside_temp_c, kwh_tariff_cop) VALUES
('UCC-PASTO', 'Universidad Cooperativa de Colombia · Campus Pasto', 'Pasto', 'SEDE_ANDINA', 13.5, 865.00);

INSERT INTO buildings (building_id, branch_id, name, floors) VALUES
('BLOQUE-A', 'UCC-PASTO', 'Bloque A', 5),
('BLOQUE-B', 'UCC-PASTO', 'Bloque B', 5);

INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns, computers) VALUES
('101-A', 'BLOQUE-A', 'Auditorio Bloque A', 'AUDITORIO', 96, 124.80, 1, 8, 12, 2),
('102-A', 'BLOQUE-A', 'Admisiones y registro', 'ADMINISTRATIVA', 12, 60.00, 1, NULL, NULL, 6),
('103-A', 'BLOQUE-A', 'Tesorería y cartera', 'ADMINISTRATIVA', 8, 40.00, 1, NULL, NULL, 4),
('104-A', 'BLOQUE-A', 'Logística interna', 'LOGISTICA', 6, 42.00, 1, NULL, NULL, 2),
('105-A', 'BLOQUE-A', 'Almacén y servicios generales', 'LOGISTICA', 6, 54.00, 1, NULL, NULL, 1),
('201-A', 'BLOQUE-A', 'Salón 201-A', 'AULA', 30, 48.00, 2, 5, 6, 1),
('202-A', 'BLOQUE-A', 'Salón 202-A', 'AULA', 30, 48.00, 2, 5, 6, 1),
('203-A', 'BLOQUE-A', 'Salón 203-A', 'AULA', 30, 48.00, 2, 5, 6, 1),
('204-A', 'BLOQUE-A', 'Salón 204-A', 'AULA', 30, 48.00, 2, 5, 6, 1),
('205-A', 'BLOQUE-A', 'Sala de estudio 205-A', 'SALA_ESTUDIO', 20, 40.00, 2, 4, 5, 0),
('301-A', 'BLOQUE-A', 'Salón 301-A', 'AULA', 30, 48.00, 3, 5, 6, 1),
('302-A', 'BLOQUE-A', 'Salón 302-A', 'AULA', 30, 48.00, 3, 5, 6, 1),
('303-A', 'BLOQUE-A', 'Salón 303-A', 'AULA', 30, 48.00, 3, 5, 6, 1),
('304-A', 'BLOQUE-A', 'Salón 304-A', 'AULA', 30, 48.00, 3, 5, 6, 1),
('305-A', 'BLOQUE-A', 'Sala de estudio 305-A', 'SALA_ESTUDIO', 20, 40.00, 3, 4, 5, 0),
('401-A', 'BLOQUE-A', 'Salón 401-A', 'AULA', 35, 56.00, 4, 5, 7, 1),
('402-A', 'BLOQUE-A', 'Salón 402-A', 'AULA', 35, 56.00, 4, 5, 7, 1),
('403-A', 'BLOQUE-A', 'Salón 403-A', 'AULA', 35, 56.00, 4, 5, 7, 1),
('404-A', 'BLOQUE-A', 'Salón 404-A', 'AULA', 35, 56.00, 4, 5, 7, 1),
('405-A', 'BLOQUE-A', 'Sala de estudio 405-A', 'SALA_ESTUDIO', 20, 40.00, 4, 4, 5, 0),
('501-A', 'BLOQUE-A', 'Decanatura y coordinaciones', 'ADMINISTRATIVA', 10, 50.00, 5, NULL, NULL, 8),
('502-A', 'BLOQUE-A', 'Sala de profesores', 'ADMINISTRATIVA', 16, 56.00, 5, NULL, NULL, 6),
('503-A', 'BLOQUE-A', 'Salón 503-A', 'AULA', 30, 48.00, 5, 5, 6, 1),
('504-A', 'BLOQUE-A', 'Salón 504-A', 'AULA', 30, 48.00, 5, 5, 6, 1),
('505-A', 'BLOQUE-A', 'Salón 505-A', 'AULA', 30, 48.00, 5, 5, 6, 1),
('101-B', 'BLOQUE-B', 'Cafetería', 'CAFETERIA', 80, 128.00, 1, 8, 10, 2),
('102-B', 'BLOQUE-B', 'Auditorio Bloque B', 'AUDITORIO', 96, 124.80, 1, 8, 12, 2),
('103-B', 'BLOQUE-B', 'Laboratorio de electrónica y Arduino', 'LABORATORIO', 24, 62.40, 1, 4, 6, 12),
('104-B', 'BLOQUE-B', 'Laboratorio de física', 'LABORATORIO', 24, 62.40, 1, 4, 6, 4),
('105-B', 'BLOQUE-B', 'Laboratorio de química', 'LABORATORIO', 24, 67.20, 1, 4, 6, 2),
('201-B', 'BLOQUE-B', 'Biblioteca · sala general', 'BIBLIOTECA', 48, 134.40, 2, NULL, NULL, 8),
('202-B', 'BLOQUE-B', 'Biblioteca · estudio en grupo', 'BIBLIOTECA', 24, 57.60, 2, 4, 6, 2),
('203-B', 'BLOQUE-B', 'Biblioteca · sala silenciosa', 'BIBLIOTECA', 30, 66.00, 2, 5, 6, 2),
('204-B', 'BLOQUE-B', 'Biblioteca · préstamo y hemeroteca', 'BIBLIOTECA', 12, 48.00, 2, 3, 4, 6),
('205-B', 'BLOQUE-B', 'Sala de cómputo 1', 'SALA_COMPUTO', 30, 60.00, 2, 5, 6, 30),
('301-B', 'BLOQUE-B', 'Sala de cómputo 2', 'SALA_COMPUTO', 30, 60.00, 3, 5, 6, 30),
('302-B', 'BLOQUE-B', 'Sala de cómputo 3', 'SALA_COMPUTO', 30, 60.00, 3, 5, 6, 30),
('303-B', 'BLOQUE-B', 'Laboratorio de redes', 'LABORATORIO', 24, 57.60, 3, 4, 6, 24),
('304-B', 'BLOQUE-B', 'Laboratorio de software', 'SALA_COMPUTO', 30, 60.00, 3, 5, 6, 30),
('305-B', 'BLOQUE-B', 'Sala de cómputo 4', 'SALA_COMPUTO', 25, 50.00, 3, 5, 5, 25),
('401-B', 'BLOQUE-B', 'Laboratorio de simulación clínica', 'LABORATORIO', 20, 60.00, 4, 4, 5, 4),
('402-B', 'BLOQUE-B', 'Laboratorio de odontología preclínica', 'LABORATORIO', 24, 72.00, 4, 4, 6, 4),
('403-B', 'BLOQUE-B', 'Laboratorio de anatomía', 'LABORATORIO', 24, 72.00, 4, 4, 6, 2),
('404-B', 'BLOQUE-B', 'Laboratorio de microbiología', 'LABORATORIO', 20, 60.00, 4, 4, 5, 2),
('405-B', 'BLOQUE-B', 'Sala de cómputo 5', 'SALA_COMPUTO', 30, 60.00, 4, 5, 6, 30),
('501-B', 'BLOQUE-B', 'Laboratorio de internet de las cosas', 'LABORATORIO', 20, 52.00, 5, 4, 5, 10),
('502-B', 'BLOQUE-B', 'Sala de cómputo 6', 'SALA_COMPUTO', 30, 60.00, 5, 5, 6, 30),
('503-B', 'BLOQUE-B', 'Sala de videoconferencias', 'AUDITORIO', 40, 64.00, 5, 5, 8, 2),
('504-B', 'BLOQUE-B', 'Sala de cómputo 7', 'SALA_COMPUTO', 30, 60.00, 5, 5, 6, 30),
('505-B', 'BLOQUE-B', 'Sala de estudio 505-B', 'SALA_ESTUDIO', 20, 40.00, 5, 4, 5, 0);

-- 4. The library's main hall has tables, not a seat grid: eight table regions drawn over its camera.
INSERT INTO study_zone_rois (zone_id, space_id, label, polygon) VALUES
('201B-M01', '201-B', 'Mesa 1', '0.0600 0.3500;0.2600 0.3500;0.2600 0.5800;0.0600 0.5800'),
('201B-M02', '201-B', 'Mesa 2', '0.2900 0.3500;0.4900 0.3500;0.4900 0.5800;0.2900 0.5800'),
('201B-M03', '201-B', 'Mesa 3', '0.5200 0.3500;0.7200 0.3500;0.7200 0.5800;0.5200 0.5800'),
('201B-M04', '201-B', 'Mesa 4', '0.7500 0.3500;0.9500 0.3500;0.9500 0.5800;0.7500 0.5800'),
('201B-M05', '201-B', 'Mesa 5', '0.0600 0.6500;0.2600 0.6500;0.2600 0.8800;0.0600 0.8800'),
('201B-M06', '201-B', 'Mesa 6', '0.2900 0.6500;0.4900 0.6500;0.4900 0.8800;0.2900 0.8800'),
('201B-M07', '201-B', 'Mesa 7', '0.5200 0.6500;0.7200 0.6500;0.7200 0.8800;0.5200 0.8800'),
('201B-M08', '201-B', 'Mesa 8', '0.7500 0.6500;0.9500 0.6500;0.9500 0.8800;0.7500 0.8800');

