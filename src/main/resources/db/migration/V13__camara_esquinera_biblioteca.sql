-- Corner camera of the floor-2 library, in the back corner of the reading room, over the silent-study tables. It is a
-- space of its own: each camera feeds one twin, and a frame from the main camera would see these tables empty.
-- Floor coordinates as for every camera (x 0..1 left to right, y 0 front .. 1 back); the camera stands at the back-left
-- corner (0, 1) looking into the room: three tables along the wall on its left, two along the back wall on its right.
-- Capacity and area are estimates (five tables of four): confirm them on site.
INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns,
                    computers, hvac_installed) VALUES
('BIBLIOTECA-2B-SILENCIO', 'BLOQUE-B', 'Biblioteca · estudio silencioso (cámara esquinera)', 'BIBLIOTECA', 20, 48.00,
 2, NULL, NULL, 0, FALSE);

INSERT INTO study_zone_rois (zone_id, space_id, label, polygon) VALUES
('BIB2B-S01', 'BIBLIOTECA-2B-SILENCIO', 'Silencio 1 · izquierda', '0.0600 0.0800;0.3000 0.0800;0.3000 0.2800;0.0600 0.2800'),
('BIB2B-S02', 'BIBLIOTECA-2B-SILENCIO', 'Silencio 2 · izquierda', '0.0600 0.3300;0.3000 0.3300;0.3000 0.5300;0.0600 0.5300'),
('BIB2B-S03', 'BIBLIOTECA-2B-SILENCIO', 'Silencio 3 · izquierda', '0.0600 0.5800;0.3000 0.5800;0.3000 0.7800;0.0600 0.7800'),
('BIB2B-S04', 'BIBLIOTECA-2B-SILENCIO', 'Silencio 4 · derecha', '0.4000 0.7200;0.6200 0.7200;0.6200 0.9400;0.4000 0.9400'),
('BIB2B-S05', 'BIBLIOTECA-2B-SILENCIO', 'Silencio 5 · derecha', '0.6800 0.7200;0.9000 0.7200;0.9000 0.9400;0.6800 0.9400');
