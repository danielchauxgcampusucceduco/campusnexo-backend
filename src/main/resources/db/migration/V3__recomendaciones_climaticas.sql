-- Climate recommendations with the decision of each policy, its explanation and the full input snapshot, so every
-- recommendation can be audited later.
CREATE TABLE climate_recommendations (
    recommendation_id UUID PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    climate_profile VARCHAR(24) NOT NULL CHECK (climate_profile IN ('SEDE_TROPICAL', 'SEDE_ANDINA', 'SEDE_TEMPLADA')),
    generated_at TIMESTAMPTZ NOT NULL,
    hvac_mode VARCHAR(24) NOT NULL CHECK (hvac_mode IN ('APAGADO', 'CALEFACCION', 'ENFRIAMIENTO', 'DESHUMIDIFICACION')),
    hvac_enabled BOOLEAN NOT NULL,
    hvac_setpoint_c DOUBLE PRECISION NOT NULL CHECK (hvac_setpoint_c BETWEEN 16 AND 30),
    hvac_explanation TEXT NOT NULL,
    ventilation_mode VARCHAR(24) NOT NULL CHECK (ventilation_mode IN ('NINGUNA', 'NATURAL', 'MECANICA')),
    ventilation_enabled BOOLEAN NOT NULL,
    ventilation_explanation TEXT NOT NULL,
    lighting_level_pct INTEGER NOT NULL CHECK (lighting_level_pct BETWEEN 0 AND 100),
    lighting_close_blinds BOOLEAN NOT NULL DEFAULT FALSE,
    lighting_explanation TEXT NOT NULL,
    explanation_text TEXT NOT NULL,
    occupancy_source VARCHAR(24) NOT NULL CHECK (occupancy_source IN ('MANUAL', 'SERVICIO_IA', 'RESPALDO_LOCAL')),
    source_telemetry_at TIMESTAMPTZ,
    input_occupancy INTEGER,
    input_co2_ppm DOUBLE PRECISION,
    input_indoor_temperature_c DOUBLE PRECISION,
    input_outside_temperature_c DOUBLE PRECISION,
    input_humidity_pct DOUBLE PRECISION,
    input_illuminance_lux DOUBLE PRECISION,
    input_hvac_mode VARCHAR(24)
        CHECK (input_hvac_mode IS NULL OR input_hvac_mode IN ('APAGADO', 'CALEFACCION', 'ENFRIAMIENTO', 'DESHUMIDIFICACION')),
    input_minutes_in_hvac_mode INTEGER
);
CREATE INDEX idx_recommendations_space_time ON climate_recommendations (space_id, generated_at DESC);
