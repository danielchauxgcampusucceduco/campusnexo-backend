CREATE TABLE climate_recommendations (
    recommendation_id UUID PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces(space_id),
    climate_profile VARCHAR(24) NOT NULL CHECK (climate_profile IN ('SEDE_TROPICAL', 'SEDE_ANDINA', 'SEDE_TEMPLADA')),
    generated_at TIMESTAMPTZ NOT NULL,
    hvac_enabled BOOLEAN NOT NULL,
    hvac_setpoint_c INTEGER NOT NULL CHECK (hvac_setpoint_c BETWEEN 16 AND 30),
    ventilation_enabled BOOLEAN NOT NULL,
    lighting_level_pct INTEGER NOT NULL CHECK (lighting_level_pct BETWEEN 0 AND 100),
    explanation_text TEXT NOT NULL
);
CREATE INDEX idx_recommendations_space_time ON climate_recommendations (space_id, generated_at DESC);
