-- Integrity of readings, climate units per room, rooms the demonstration may touch and typed authorship
-- of reservations.

-- 1. Raw camera count, kept apart from the twin's fused estimate (people_count). The AI service fuses
--    camera, CO2 and Wi-Fi itself: sending it the fused value would count the CO2 and Wi-Fi evidence twice.
ALTER TABLE telemetry_stream ADD COLUMN camera_people INTEGER CHECK (camera_people >= 0);

-- 2. One reading per space and instant. A redelivered MQTT message (QoS 1) cannot create a second row,
--    even from two backend instances at once. Older duplicates, if any, are removed first (keeping the first).
DELETE FROM telemetry_stream duplicate
USING telemetry_stream original
WHERE duplicate.space_id = original.space_id
  AND duplicate.recorded_at = original.recorded_at
  AND duplicate.id > original.id;
ALTER TABLE telemetry_stream ADD CONSTRAINT uq_telemetry_space_instant UNIQUE (space_id, recorded_at);
-- The unique index serves the same (space_id, recorded_at) lookups, in both directions.
DROP INDEX IF EXISTS idx_telemetry_space_time;

-- 3. Rooms with a climate unit the twin can command. Prototype assumption, to be confirmed with the campus
--    maintenance office: classrooms, laboratories, computer rooms and auditoriums have one; offices, storage,
--    the library, the cafeteria and common areas do not. A room without a unit is never climatized and
--    reports no energy savings.
ALTER TABLE spaces ADD COLUMN hvac_installed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE spaces SET hvac_installed = TRUE WHERE space_type IN ('AULA', 'LABORATORIO', 'SALA_COMPUTO', 'AUDITORIO');

-- 4. Rooms the live demonstration may change. Every other room is left untouched by the scenarios, including
--    the global reset.
ALTER TABLE spaces ADD COLUMN simulation_allowed BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE spaces SET simulation_allowed = TRUE WHERE space_id IN ('420-A', '421-A', '422-A', '423-A', '503-A', '402-B',
    '414-B', '309-A', '3B-DE-BIOPROCESOS-LABORATORIO', '2B-LABORATORIO-MULTIFUNCIONAL', 'BIBLIOTECA-2B');

-- 5. Who created a reservation and from where, as columns instead of a prefix in the free-text reference.
ALTER TABLE reservations ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'USUARIO'
    CHECK (origin IN ('USUARIO', 'SIMULACION'));
UPDATE reservations SET origin = 'SIMULACION' WHERE reference LIKE 'SIM-%';
ALTER TABLE reservations ADD COLUMN created_by VARCHAR(64);

-- 6. New HVAC strategies of the twin: pre-heating (cold climates) and rooms without a climate unit.
ALTER TABLE space_twin_states DROP CONSTRAINT IF EXISTS space_twin_states_hvac_state_check;
ALTER TABLE space_twin_states ADD CONSTRAINT space_twin_states_hvac_state_check CHECK (hvac_state IN ('APAGADO',
    'PRE_ENFRIAMIENTO_IA', 'PRE_CALENTAMIENTO_IA', 'CONFORT_ACTIVO', 'ECO_SETBACK_ENTRE_CLASES', 'CORTE_INERCIA_TERMICA',
    'BLOQUEO_AHORRO_VACIO', 'VENTILACION_CO2_ACTIVA', 'OVERRIDE_MANUAL_DOCENTE', 'SIN_CLIMATIZACION'));
