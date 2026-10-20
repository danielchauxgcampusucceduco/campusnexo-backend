ALTER TABLE climate_recommendations
    ADD COLUMN hvac_explanation TEXT,
    ADD COLUMN ventilation_explanation TEXT,
    ADD COLUMN lighting_explanation TEXT;

UPDATE climate_recommendations
SET hvac_explanation = COALESCE(NULLIF(split_part(explanation_text, '; ', 1), ''), 'Decisión anterior; consultar explanation_text'),
    ventilation_explanation = COALESCE(NULLIF(split_part(explanation_text, '; ', 2), ''), 'Decisión anterior; consultar explanation_text'),
    lighting_explanation = COALESCE(NULLIF(split_part(explanation_text, '; ', 3), ''), 'Decisión anterior; consultar explanation_text');

ALTER TABLE climate_recommendations
    ALTER COLUMN hvac_explanation SET NOT NULL,
    ALTER COLUMN ventilation_explanation SET NOT NULL,
    ALTER COLUMN lighting_explanation SET NOT NULL;
