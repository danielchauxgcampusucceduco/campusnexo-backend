-- The hemeroteca (floor 3 of block A) has two rooms of its own in its corners: one for tutoring and one for private
-- study. Each is a space of its own, so it is offered as available on its own. Measurements are estimates (the plan of
-- the hemeroteca is 18.7 x 14.3 m): confirm them on site and adjust them in Administración → Espacios.
--   * Sala de asesorías: front-left corner, 4.5 x 4 m, a table for six.
--   * Sala de estudio privado: back-right corner, 4 x 4 m, two desks.
-- The hemeroteca's own area no longer counts the two rooms.
INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns,
                    computers) VALUES
('HEMEROTECA-3A-ASESORIAS', 'BLOQUE-A', 'Hemeroteca · sala de asesorías', 'SALA_ESTUDIO', 6, 18.00, 3, 2, 3, 0),
('HEMEROTECA-3A-ESTUDIO', 'BLOQUE-A', 'Hemeroteca · sala de estudio privado', 'SALA_ESTUDIO', 2, 16.00, 3, 1, 2, 0);

UPDATE spaces SET area_sqm = area_sqm - 34.00 WHERE space_id = 'HEMEROTECA-3A';
