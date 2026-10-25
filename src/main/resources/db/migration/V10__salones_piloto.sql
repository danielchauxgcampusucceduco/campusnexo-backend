-- Pilot rooms of the stakeholder demonstration.
-- Neither room is in the 2025-B plan (floor 3 of block A only shows 308-A to 312-A), so they have no outline on the
-- campus map. Their measurements are ILLUSTRATIVE: replace them with the measured ones before installing the camera.
--   301-A: computer room, 10 x 6 m, 30 workstations in 5 rows of 6. Its inventory is NOT seeded: the camera
--          proposes the workstations (table, chair and paired computer) and an administrator confirms them.
--   303-A: classroom, 9 x 6 m, 36 desks in 6 x 6 like every classroom of the campus, each with table and chair.
INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns,
                    computers, hvac_installed) VALUES
('301-A', 'BLOQUE-A', 'Sala de cómputo 301-A (piloto)', 'SALA_COMPUTO', 30, 60.00, 3, 5, 6, 30, TRUE),
('303-A', 'BLOQUE-A', 'Salón 303-A (piloto)', 'AULA', 36, 54.00, 3, 6, 6, 1, TRUE);

-- One table and one chair per desk; codes follow the labels the inventory office sticks on them (303A-SILLA-07).
INSERT INTO activos (codigo, tipo, space_id, numero_puesto)
SELECT format('303A-%s-%s', kind.tipo, lpad(seat::text, 2, '0')), kind.tipo, '303-A', seat
FROM generate_series(1, 36) AS seat
CROSS JOIN (VALUES ('MESA'), ('SILLA')) AS kind (tipo);
