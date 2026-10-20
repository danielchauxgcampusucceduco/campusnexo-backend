-- Decimal setpoints (the stored value is the one the policy applied), explicit modes and
-- the full input snapshot of each recommendation for auditing.
ALTER TABLE climate_recommendations
    ALTER COLUMN hvac_setpoint_c TYPE DOUBLE PRECISION,
    ADD COLUMN hvac_mode VARCHAR(24),
    ADD COLUMN ventilation_mode VARCHAR(24),
    ADD COLUMN lighting_close_blinds BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN occupancy_source VARCHAR(24) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN source_telemetry_at TIMESTAMPTZ,
    ADD COLUMN input_occupancy INTEGER,
    ADD COLUMN input_co2_ppm DOUBLE PRECISION,
    ADD COLUMN input_indoor_temperature_c DOUBLE PRECISION,
    ADD COLUMN input_outside_temperature_c DOUBLE PRECISION,
    ADD COLUMN input_humidity_pct DOUBLE PRECISION,
    ADD COLUMN input_illuminance_lux DOUBLE PRECISION,
    ADD COLUMN input_hvac_mode VARCHAR(24),
    ADD COLUMN input_minutes_in_hvac_mode INTEGER;

-- Rows created before V6 only knew on/off; the old policy could only cool.
UPDATE climate_recommendations
SET hvac_mode = CASE WHEN hvac_enabled THEN 'ENFRIAMIENTO' ELSE 'APAGADO' END,
    ventilation_mode = CASE WHEN ventilation_enabled THEN 'MECANICA' ELSE 'NINGUNA' END;

ALTER TABLE climate_recommendations
    ALTER COLUMN hvac_mode SET NOT NULL,
    ALTER COLUMN ventilation_mode SET NOT NULL,
    ADD CONSTRAINT chk_recommendation_hvac_mode
        CHECK (hvac_mode IN ('APAGADO', 'CALEFACCION', 'ENFRIAMIENTO', 'DESHUMIDIFICACION')),
    ADD CONSTRAINT chk_recommendation_ventilation_mode
        CHECK (ventilation_mode IN ('NINGUNA', 'NATURAL', 'MECANICA')),
    ADD CONSTRAINT chk_recommendation_occupancy_source
        CHECK (occupancy_source IN ('MANUAL', 'SERVICIO_IA', 'RESPALDO_LOCAL')),
    ADD CONSTRAINT chk_recommendation_input_hvac_mode
        CHECK (input_hvac_mode IS NULL OR input_hvac_mode IN ('APAGADO', 'CALEFACCION', 'ENFRIAMIENTO', 'DESHUMIDIFICACION'));
