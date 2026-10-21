-- Campus fact confirmed on 2026-10-21: every classroom (AULA) of the UCC Pasto campus has 36 individual
-- desks, laid out in 6 rows of 6 under the ceiling camera. This replaces the estimate by area of V12.
UPDATE spaces SET capacity = 36, seat_rows = 6, seat_columns = 6
WHERE space_type = 'AULA' AND building_id IN ('BLOQUE-A', 'BLOQUE-B');

-- Their twins were computed with the old desk grid: start them again from the new one.
DELETE FROM space_twin_states
WHERE space_id IN (SELECT space_id FROM spaces WHERE space_type = 'AULA' AND building_id IN ('BLOQUE-A', 'BLOQUE-B'));
