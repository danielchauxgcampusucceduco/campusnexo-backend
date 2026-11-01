-- Corrections of the campus with the administration (November 2026):
--   * Block A, the one with the elevator, holds the library, the newspaper library, the cafeteria and the computer rooms
--     (floors 3 and 5); V9 had placed the library, the newspaper library, the cafeteria and the floor-5 computer room
--     in block B. They move to block A, and their codes change with the block.
--   * Three codes carried typos (INVESSTIGACIONES, FOREING, REAL-LIDAD).
--   * Block B, the first one, holds the engineering faculty; the 2620 timetable of Software Engineering uses
--     classrooms of block B that the 2025-B plan does not show. They are added like every classroom of the campus
--     (36 desks in 6 x 6); their area is an estimate to confirm on site, and they have no outline on the map.
-- A renamed space keeps everything that points at it. Its twin state is dropped: it is derived from the readings and
-- the twin rebuilds it with the next one (its JSON document embeds the old code and the old table regions).

CREATE FUNCTION pg_temp.renombrar(viejo VARCHAR, nuevo VARCHAR, bloque VARCHAR) RETURNS VOID AS $$
BEGIN
    INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns,
                        computers, hvac_installed)
    SELECT nuevo, bloque, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns, computers, hvac_installed
    FROM spaces WHERE space_id = viejo;
    UPDATE telemetry_stream SET space_id = nuevo WHERE space_id = viejo;
    UPDATE climate_recommendations SET space_id = nuevo WHERE space_id = viejo;
    UPDATE occupancy_estimates SET space_id = nuevo WHERE space_id = viejo;
    UPDATE alerts SET space_id = nuevo WHERE space_id = viejo;
    UPDATE reservations SET space_id = nuevo WHERE space_id = viejo;
    UPDATE study_zone_rois SET space_id = nuevo WHERE space_id = viejo;
    UPDATE activos SET space_id = nuevo WHERE space_id = viejo;
    UPDATE propuestas_puestos SET space_id = nuevo WHERE space_id = viejo;
    UPDATE emparejamientos_cpu SET space_id = nuevo WHERE space_id = viejo;
    UPDATE agentes_cpu SET space_id = nuevo WHERE space_id = viejo;
    DELETE FROM space_twin_states WHERE space_id = viejo;
    DELETE FROM spaces WHERE space_id = viejo;
END;
$$ LANGUAGE plpgsql;

SELECT pg_temp.renombrar('BIBLIOTECA-2B', 'BIBLIOTECA-2A', 'BLOQUE-A');
SELECT pg_temp.renombrar('BIBLIOTECA-2B-SILENCIO', 'BIBLIOTECA-2A-SILENCIO', 'BLOQUE-A');
SELECT pg_temp.renombrar('2B-SALA-DE-CONSULTA-DIGITAL', '2A-SALA-DE-CONSULTA-DIGITAL', 'BLOQUE-A');
SELECT pg_temp.renombrar('HEMEROTECA-3B', 'HEMEROTECA-3A', 'BLOQUE-A');
SELECT pg_temp.renombrar('CAFETERIA-1B', 'CAFETERIA-1A', 'BLOQUE-A');
SELECT pg_temp.renombrar('5B-INFORMATICA-AULA-DE', '5A-AULA-DE-INFORMATICA', 'BLOQUE-A');
SELECT pg_temp.renombrar('2A-INVESSTIGACIONES-MEDICAS-CE', '2A-INVESTIGACIONES-MEDICAS', 'BLOQUE-A');
SELECT pg_temp.renombrar('5A-FOREING-LANGUAGE-CENTER', '5A-FOREIGN-LANGUAGE-CENTER', 'BLOQUE-A');
SELECT pg_temp.renombrar('2B-REAL-LIDAD-LABORATORIO-MIXT', '2B-LABORATORIO-REALIDAD-MIXTA', 'BLOQUE-B');

-- The table regions follow their room: BIB2B-* become BIB2A-* (the dropped twin states held their observations).
UPDATE study_zone_rois SET zone_id = 'BIB2A-' || substring(zone_id FROM 7) WHERE zone_id LIKE 'BIB2B-%';

-- The floor-2 library rooms keep a readable name; the computer room of floor 5 gets the name its door shows.
UPDATE spaces SET name = 'Aula de Informática · piso 5' WHERE space_id = '5A-AULA-DE-INFORMATICA';

-- Classrooms of block B that the 2620 timetable of Software Engineering uses.
INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns,
                    computers, hvac_installed) VALUES
('302-B', 'BLOQUE-B', 'Salón 302-B', 'AULA', 36, 54.00, 3, 6, 6, 1, TRUE),
('303-B', 'BLOQUE-B', 'Salón 303-B', 'AULA', 36, 54.00, 3, 6, 6, 1, TRUE),
('304-B', 'BLOQUE-B', 'Salón 304-B', 'AULA', 36, 54.00, 3, 6, 6, 1, TRUE),
('305-B', 'BLOQUE-B', 'Salón 305-B', 'AULA', 36, 54.00, 3, 6, 6, 1, TRUE),
('408-B', 'BLOQUE-B', 'Salón 408-B', 'AULA', 36, 54.00, 4, 6, 6, 1, TRUE),
('501-B', 'BLOQUE-B', 'Salón 501-B', 'AULA', 36, 54.00, 5, 6, 6, 1, TRUE),
('503-B', 'BLOQUE-B', 'Salón 503-B', 'AULA', 36, 54.00, 5, 6, 6, 1, TRUE),
('601-B', 'BLOQUE-B', 'Salón 601-B', 'AULA', 36, 54.00, 6, 6, 6, 1, TRUE);
