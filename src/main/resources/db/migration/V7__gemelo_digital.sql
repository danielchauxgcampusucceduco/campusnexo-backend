-- Desk regions drawn over a camera where the seats are not a grid (library tables), in normalized image
-- coordinates ("x y;x y;..."). Rooms with a seat grid derive their regions from it.
CREATE TABLE study_zone_rois (
    zone_id VARCHAR(32) PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    label VARCHAR(60) NOT NULL,
    polygon TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rois_space ON study_zone_rois (space_id);

-- Latest digital-twin state per space. The document holds the full state, including the Kalman and thermal
-- traces; status and HVAC state are columns for SQL reporting. Every write presents the revision it read
-- (optimistic concurrency).
CREATE TABLE space_twin_states (
    space_id VARCHAR(32) PRIMARY KEY REFERENCES spaces (space_id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('LIBRE', 'RESERVADO_EN_ESPERA', 'OCUPADO_OPTIMO',
        'ALERTA_SUBUTILIZADO', 'ALERTA_SOBRECUPO', 'LIBERADO_POR_NO_SHOW', 'HABILITADO_POPUP_PARCIALES',
        'MANTENIMIENTO')),
    hvac_state VARCHAR(32) NOT NULL CHECK (hvac_state IN ('APAGADO', 'PRE_ENFRIAMIENTO_IA', 'PRE_CALENTAMIENTO_IA',
        'CONFORT_ACTIVO', 'ECO_SETBACK_ENTRE_CLASES', 'CORTE_INERCIA_TERMICA', 'BLOQUEO_AHORRO_VACIO',
        'VENTILACION_CO2_ACTIVA', 'OVERRIDE_MANUAL_DOCENTE', 'SIN_CLIMATIZACION')),
    updated_at TIMESTAMPTZ NOT NULL,
    document TEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);
