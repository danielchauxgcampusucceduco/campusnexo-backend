-- The campus works with the vision camera only: there is no room board (ESP32 with temperature, CO2, light and current
-- sensors and relays), so the climate control, the energy accounting and their alerts go.
--   * the climate recommendations and the environment readings stored with each frame;
--   * the climate profile, outside temperature and energy tariff of the branches, and whether a room has a climate unit;
--   * the alerts of CO2, energy in empty rooms and sensor health, which no longer exist;
--   * the twin states: their stored document (version 1) carried the climate control and is not read any more; each room
--     starts again with its next camera frame.
DROP TABLE IF EXISTS climate_recommendations;

ALTER TABLE telemetry_stream
    DROP COLUMN IF EXISTS co2_ppm,
    DROP COLUMN IF EXISTS temperature_c,
    DROP COLUMN IF EXISTS humidity_pct,
    DROP COLUMN IF EXISTS illuminance_lux,
    DROP COLUMN IF EXISTS active_power_kw;
-- The campus Wi-Fi count is optional now: most frames carry none.
ALTER TABLE telemetry_stream ALTER COLUMN wifi_devices DROP NOT NULL;

ALTER TABLE spaces DROP COLUMN IF EXISTS hvac_installed;
ALTER TABLE campus_branches
    DROP COLUMN IF EXISTS climate_profile,
    DROP COLUMN IF EXISTS avg_outside_temp_c,
    DROP COLUMN IF EXISTS kwh_tariff_cop;

DELETE FROM alerts WHERE alert_type IN ('CO2_ALTO', 'CONSUMO_ESPACIO_VACIO', 'SENSOR_ANOMALO');
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS alerts_alert_type_check;
-- HORARIO_SIN_USO: a class of the academic timetable under way with nobody in the room.
ALTER TABLE alerts ADD CONSTRAINT alerts_alert_type_check CHECK (alert_type IN ('SOBRECUPO', 'RESERVA_SIN_USO',
    'CPU_ENCENDIDA_SIN_USUARIO', 'ACTIVO_FALTANTE', 'FALLO_CAMARA', 'HORARIO_SIN_USO'));

DELETE FROM space_twin_states;
ALTER TABLE space_twin_states DROP COLUMN IF EXISTS hvac_state;
